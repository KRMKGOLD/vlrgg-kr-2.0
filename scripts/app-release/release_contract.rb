# frozen_string_literal: true

require "fileutils"
require "base64"
require "digest"
require "json"
require "open3"
require "pathname"

module ReleaseContract
  class Error < StandardError; end

  VERSION_PATTERN = /\A[0-9]+(?:\.[0-9]+){0,2}\z/
  SHA_PATTERN = /\A[0-9a-f]{40}\z/
  MAX_ANDROID_BUILD_NUMBER = 2_100_000_000

  module_function

  def validate_inputs!(environment = ENV)
    version = required(environment, "APP_VERSION")
    build_number = required(environment, "APP_BUILD_NUMBER")
    source_sha = required(environment, "SOURCE_SHA")

    raise Error, "APP_VERSION must contain one to three numeric components." unless VERSION_PATTERN.match?(version)
    unless /\A[0-9]+\z/.match?(build_number) && build_number.to_i.between?(1, MAX_ANDROID_BUILD_NUMBER)
      raise Error, "APP_BUILD_NUMBER must be a positive Android-compatible integer."
    end
    raise Error, "SOURCE_SHA must be a full lowercase commit SHA." unless SHA_PATTERN.match?(source_sha)

    true
  end

  def validate_workflow_source!(environment = ENV, repository_root = default_repository_root, head_sha: nil)
    source_sha = required(environment, "SOURCE_SHA")
    github_sha = required(environment, "GITHUB_SHA")
    workspace = required(environment, "GITHUB_WORKSPACE")
    raise Error, "Release lanes run only from the manual GitHub Actions workflows." unless
      environment["GITHUB_ACTIONS"] == "true" &&
      environment["GITHUB_EVENT_NAME"] == "workflow_dispatch" &&
      environment["GITHUB_REF"] == "refs/heads/main"
    raise Error, "The checked-out workflow source does not match SOURCE_SHA." unless
      github_sha == source_sha &&
      File.expand_path(workspace) == File.expand_path(repository_root)

    if head_sha.nil?
      stdout, _stderr, status = Open3.capture3("git", "-C", repository_root, "rev-parse", "HEAD")
      head_sha = stdout.strip if status.success?
    end
    raise Error, "The checked-out workflow source does not match SOURCE_SHA." unless head_sha == source_sha

    true
  end

  def verify_ci!(json, source_sha)
    raise Error, "SOURCE_SHA must be a full lowercase commit SHA." unless SHA_PATTERN.match?(source_sha)

    runs = JSON.parse(json).fetch("workflow_runs")
    raise Error, "The CI verification response was malformed." unless runs.is_a?(Array)
    matched = runs.any? do |run|
      run["head_sha"] == source_sha &&
        run["head_branch"] == "main" &&
        run["event"] == "push" &&
        run["status"] == "completed" &&
        run["conclusion"] == "success"
    end
    raise Error, "The frozen source SHA has no successful main push CI run." unless matched

    true
  rescue JSON::ParserError, KeyError, TypeError
    raise Error, "The CI verification response was malformed."
  end

  def cleanup_android!(environment = ENV, repository_root = default_repository_root)
    signing_directory = environment["ANDROID_SIGNING_TEMP_DIR"]
    runner_temp = environment["RUNNER_TEMP"]
    if safe_child?(signing_directory, runner_temp) && File.basename(signing_directory).start_with?("vlrgg-signing.")
      FileUtils.rm_rf(signing_directory)
    end
    FileUtils.rm_rf(File.join(repository_root, "app/androidApp/build/outputs/bundle/release"))
  end

  def cleanup_ios!(environment = ENV, repository_root = default_repository_root, delete_keychain: nil)
    home = environment["HOME"]
    runner_temp = environment["RUNNER_TEMP"]
    keychain_marker = environment["IOS_KEYCHAIN_MARKER_PATH"]
    profile_marker = environment["IOS_PROFILE_MARKER_PATH"]
    errors = []

    if marker_file?(keychain_marker, runner_temp)
      keychain_path = read_marker(keychain_marker, runner_temp)
      valid_keychain = keychain_path && safe_child?(keychain_path, File.join(home.to_s, "Library/Keychains")) &&
                       File.basename(keychain_path).start_with?("vlrgg-release-")
      if !valid_keychain
        errors << "The iOS keychain cleanup marker is invalid; it was retained."
      elsif !File.exist?(keychain_path)
        remove_marked_file(keychain_marker, runner_temp)
      else
        delete_keychain ||= lambda do |path|
          system("/usr/bin/security", "delete-keychain", path, out: File::NULL, err: File::NULL)
        end
        deleted = begin
          delete_keychain.call(keychain_path)
        rescue StandardError
          false
        end
        if deleted && !File.exist?(keychain_path)
          remove_marked_file(keychain_marker, runner_temp)
        else
          errors << "The temporary iOS keychain could not be removed; its cleanup marker was retained."
        end
      end
    end

    if marker_file?(profile_marker, runner_temp)
      profile = read_profile_marker(profile_marker, runner_temp)
      profile_path = profile && profile.fetch("path")
      if profile.nil? || !provisioning_profile_path?(profile_path, home) || File.extname(profile_path) != ".mobileprovision"
        errors << "The iOS provisioning profile cleanup marker is invalid; it was retained."
      elsif !File.exist?(profile_path)
        remove_marked_file(profile_marker, runner_temp)
      else
        begin
          if Digest::SHA256.file(profile_path).hexdigest == profile.fetch("sha256")
            FileUtils.rm_f(profile_path)
            if File.exist?(profile_path)
              errors << "The temporary iOS provisioning profile could not be removed; its cleanup marker was retained."
            else
              remove_marked_file(profile_marker, runner_temp)
            end
          else
            errors << "The installed provisioning profile changed after installation; it and its cleanup marker were retained."
          end
        rescue StandardError
          errors << "The temporary iOS provisioning profile could not be verified or removed; its cleanup marker was retained."
        end
      end
    end

    %w[export archive derived-data].each do |directory|
      begin
        FileUtils.rm_rf(File.join(repository_root, "app/iosApp/build", directory))
      rescue StandardError
        errors << "An iOS release output could not be removed."
      end
    end
    raise Error, errors.join(" ") unless errors.empty?
  end

  def safe_child?(path, parent)
    return false if path.nil? || parent.nil? || path.empty? || parent.empty?

    expanded_path = Pathname.new(path).expand_path
    expanded_parent = Pathname.new(parent).expand_path
    expanded_path != expanded_parent && expanded_path.to_s.start_with?("#{expanded_parent}#{File::SEPARATOR}")
  end

  def provisioning_profile_path?(path, home)
    return false if home.nil? || home.empty?

    [
      File.join(home, "Library/Developer/Xcode/UserData/Provisioning Profiles"),
      File.join(home, "Library/MobileDevice/Provisioning Profiles")
    ].any? { |parent| safe_child?(path, parent) }
  end

  def write_marker(path, value, runner_temp)
    raise Error, "Cleanup marker path is outside the runner temporary directory." unless safe_child?(path, runner_temp)

    File.open(path, File::WRONLY | File::CREAT | File::EXCL, 0o600) { |file| file.write("#{value}\n") }
  rescue Errno::EEXIST
    raise Error, "Cleanup marker already exists; refusing to overwrite it."
  end

  def write_profile_marker(marker_path, profile_path, installed_path, runner_temp)
    content = JSON.generate(
      "path" => installed_path,
      "sha256" => Digest::SHA256.file(profile_path).hexdigest
    )
    write_marker(marker_path, content, runner_temp)
  end

  def ensure_absent!(path, description)
    raise Error, "#{description} already exists; refusing to overwrite it." if File.exist?(path)

    true
  end

  def write_base64_secret!(name, path, environment = ENV)
    value = required(environment, name)
    decoded = Base64.strict_decode64(value)
    created = false
    File.open(path, File::WRONLY | File::CREAT | File::EXCL, 0o600) do |file|
      created = true
      file.binmode
      file.write(decoded)
    end
    true
  rescue ArgumentError
    raise Error, "Invalid base64 release input: #{name}."
  rescue Errno::EEXIST
    raise Error, "Temporary release credential already exists; refusing to overwrite it."
  rescue StandardError
    FileUtils.rm_f(path) if created
    raise
  end

  def required(environment, name)
    value = environment[name]
    raise Error, "Required release input is missing: #{name}." if value.nil? || value.empty?

    value
  end

  def read_marker(marker_path, runner_temp)
    return nil unless safe_child?(marker_path, runner_temp) && File.file?(marker_path)

    File.read(marker_path, encoding: "UTF-8").strip
  end

  def read_profile_marker(marker_path, runner_temp)
    record = JSON.parse(read_marker(marker_path, runner_temp).to_s)
    return nil unless record.is_a?(Hash) && record.keys.sort == %w[path sha256]
    return nil unless record["path"].is_a?(String) && /\A[0-9a-f]{64}\z/.match?(record["sha256"])

    record
  rescue JSON::ParserError
    nil
  end

  def marker_file?(marker_path, runner_temp)
    safe_child?(marker_path, runner_temp) && File.file?(marker_path)
  end

  def remove_marked_file(path, runner_temp)
    FileUtils.rm_f(path) if safe_child?(path, runner_temp)
  end

  def default_repository_root
    File.expand_path("../..", __dir__)
  end
end

if $PROGRAM_NAME == __FILE__
  begin
    command = ARGV.shift
    case command
    when "validate-inputs"
      ReleaseContract.validate_inputs!
    when "validate-workflow-source"
      ReleaseContract.validate_workflow_source!
    when "verify-ci"
      ReleaseContract.verify_ci!(STDIN.read, ARGV.fetch(0))
    when "cleanup-android"
      ReleaseContract.cleanup_android!
    when "cleanup-ios"
      ReleaseContract.cleanup_ios!
    when "write-base64-secret"
      ReleaseContract.write_base64_secret!(ARGV.fetch(0), ARGV.fetch(1))
    else
      raise ReleaseContract::Error, "Unknown release contract command."
    end
  rescue ReleaseContract::Error => error
    warn error.message
    exit 1
  end
end
