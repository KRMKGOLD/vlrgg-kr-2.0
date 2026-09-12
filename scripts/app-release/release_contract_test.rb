# frozen_string_literal: true

require "minitest/autorun"
require "tmpdir"
require "yaml"
require_relative "release_contract"

class ReleaseContractTest < Minitest::Test
  ROOT = File.expand_path("../..", __dir__)

  def test_validates_shared_release_inputs
    assert ReleaseContract.validate_inputs!(
      "APP_VERSION" => "1.2.3",
      "APP_BUILD_NUMBER" => "42",
      "SOURCE_SHA" => "a" * 40
    )

    ["", "1.2.3.4", "1.beta"].each do |version|
      assert_raises(ReleaseContract::Error) do
        ReleaseContract.validate_inputs!(valid_environment.merge("APP_VERSION" => version))
      end
    end
    ["0", "-1", "2100000001"].each do |build_number|
      assert_raises(ReleaseContract::Error) do
        ReleaseContract.validate_inputs!(valid_environment.merge("APP_BUILD_NUMBER" => build_number))
      end
    end
  end

  def test_requires_successful_main_push_ci_for_exact_sha
    source_sha = "b" * 40
    valid_run = {
      "head_sha" => source_sha,
      "head_branch" => "main",
      "event" => "push",
      "status" => "completed",
      "conclusion" => "success"
    }
    assert ReleaseContract.verify_ci!(JSON.generate("workflow_runs" => [valid_run]), source_sha)

    %w[head_sha head_branch event status conclusion].each do |field|
      assert_raises(ReleaseContract::Error) do
        ReleaseContract.verify_ci!(JSON.generate("workflow_runs" => [valid_run.merge(field => "wrong")]), source_sha)
      end
    end
  end

  def test_requires_manual_workflow_checkout_to_match_source_sha
    Dir.mktmpdir do |repository|
      git = lambda do |*arguments|
        output, error, status = Open3.capture3("git", "-C", repository, *arguments)
        assert status.success?, error
        output.strip
      end
      git.call("init", "--quiet")
      File.write(File.join(repository, "source.txt"), "committed")
      git.call("add", "source.txt")
      git.call("-c", "user.name=Release Test", "-c", "user.email=release@example.invalid", "commit", "--quiet", "-m", "fixture")
      source_sha = git.call("rev-parse", "HEAD")
      environment = valid_environment.merge(
        "SOURCE_SHA" => source_sha,
        "GITHUB_ACTIONS" => "true",
        "GITHUB_EVENT_NAME" => "workflow_dispatch",
        "GITHUB_REF" => "refs/heads/main",
        "GITHUB_SHA" => source_sha,
        "GITHUB_WORKSPACE" => repository
      )
      assert ReleaseContract.validate_workflow_source!(environment, repository)

      %w[GITHUB_ACTIONS GITHUB_EVENT_NAME GITHUB_REF GITHUB_SHA GITHUB_WORKSPACE].each do |name|
        assert_raises(ReleaseContract::Error) do
          ReleaseContract.validate_workflow_source!(environment.merge(name => "wrong"), repository)
        end
      end
      assert_raises(ReleaseContract::Error) do
        ReleaseContract.validate_workflow_source!(environment.merge("SOURCE_SHA" => "d" * 40, "GITHUB_SHA" => "d" * 40), repository)
      end
      assert_raises(ReleaseContract::Error) do
        ReleaseContract.validate_workflow_source!(environment.reject { |name, _value| name == "GITHUB_WORKSPACE" }, repository)
      end

      File.write(File.join(repository, "source.txt"), "modified")
      assert_raises(ReleaseContract::Error) { ReleaseContract.validate_workflow_source!(environment, repository) }
      git.call("add", "source.txt")
      assert_raises(ReleaseContract::Error) { ReleaseContract.validate_workflow_source!(environment, repository) }
      git.call("reset", "--hard", "HEAD")
      File.write(File.join(repository, "untracked.txt"), "untracked")
      assert_raises(ReleaseContract::Error) { ReleaseContract.validate_workflow_source!(environment, repository) }
      File.delete(File.join(repository, "untracked.txt"))
      assert ReleaseContract.validate_workflow_source!(environment, repository)
    end
  end

  def test_cleanup_markers_cannot_escape_runner_temp
    Dir.mktmpdir do |directory|
      marker = File.join(directory, "marker")
      ReleaseContract.write_marker(marker, "safe", directory)
      assert_equal "safe", File.read(marker).strip
      assert_equal 0o600, File.stat(marker).mode & 0o777
      assert_raises(ReleaseContract::Error) { ReleaseContract.write_marker(marker, "replace", directory) }
      assert_equal "safe", File.read(marker).strip
      refute ReleaseContract.safe_child?(directory, directory)
      refute ReleaseContract.safe_child?(File.join(directory, "..", "outside"), directory)
      assert ReleaseContract.provisioning_profile_path?(
        File.join(directory, "Library/Developer/Xcode/UserData/Provisioning Profiles/test.mobileprovision"),
        directory
      )
      assert ReleaseContract.provisioning_profile_path?(
        File.join(directory, "Library/MobileDevice/Provisioning Profiles/test.mobileprovision"),
        directory
      )
    end
  end

  def test_secret_materialization_is_private_and_collision_safe
    Dir.mktmpdir do |directory|
      path = File.join(directory, "secret")
      environment = { "ENCODED" => ["secret"].pack("m0") }
      assert ReleaseContract.write_base64_secret!("ENCODED", path, environment)
      assert_equal "secret", File.binread(path)
      assert_equal 0o600, File.stat(path).mode & 0o777
      assert_raises(ReleaseContract::Error) do
        ReleaseContract.write_base64_secret!("ENCODED", path, environment.merge("ENCODED" => ["replace"].pack("m0")))
      end
      assert_equal "secret", File.binread(path)
    end
  end

  def test_ios_cleanup_preserves_preexisting_credentials_and_profiles
    Dir.mktmpdir do |directory|
      runner_temp = File.join(directory, "runner")
      home = File.join(directory, "home")
      api_key = File.join(home, ".appstoreconnect/private_keys/AuthKey_existing.p8")
      installed_profile = File.join(
        home,
        "Library/Developer/Xcode/UserData/Provisioning Profiles/existing.mobileprovision"
      )
      [api_key, installed_profile].each { |path| FileUtils.mkdir_p(File.dirname(path)) }
      File.binwrite(api_key, "user-api-key")
      File.binwrite(installed_profile, "user-profile")
      FileUtils.mkdir_p(runner_temp)

      assert_raises(ReleaseContract::Error) do
        ReleaseContract.ensure_absent!(installed_profile, "The target iOS provisioning profile")
      end
      ReleaseContract.cleanup_ios!(
        {
          "HOME" => home,
          "RUNNER_TEMP" => runner_temp,
          "APP_STORE_CONNECT_KEY_ID" => "existing"
        },
        File.join(directory, "repository")
      )

      assert_equal "user-api-key", File.binread(api_key)
      assert_equal "user-profile", File.binread(installed_profile)
    end
  end

  def test_ios_cleanup_retains_failed_keychain_marker_but_cleans_independent_owned_files
    Dir.mktmpdir do |directory|
      repository = File.join(directory, "repository")
      runner_temp = File.join(directory, "runner")
      home = File.join(directory, "home")
      keychain_marker = File.join(runner_temp, "keychain-marker")
      profile_marker = File.join(runner_temp, "profile-marker")
      keychain = File.join(home, "Library/Keychains/vlrgg-release-test.keychain-db")
      profile_source = File.join(runner_temp, "source.mobileprovision")
      installed_profile = File.join(
        home,
        "Library/Developer/Xcode/UserData/Provisioning Profiles/owned.mobileprovision"
      )
      ios_output = File.join(repository, "app/iosApp/build/export/app.ipa")
      [keychain, profile_source, installed_profile, ios_output].each do |path|
        FileUtils.mkdir_p(File.dirname(path))
        File.binwrite(path, "owned")
      end
      ReleaseContract.write_marker(keychain_marker, keychain, runner_temp)
      ReleaseContract.write_profile_marker(profile_marker, profile_source, installed_profile, runner_temp)
      environment = {
        "HOME" => home,
        "RUNNER_TEMP" => runner_temp,
        "IOS_KEYCHAIN_MARKER_PATH" => keychain_marker,
        "IOS_PROFILE_MARKER_PATH" => profile_marker
      }

      assert_raises(ReleaseContract::Error) do
        ReleaseContract.cleanup_ios!(environment, repository, delete_keychain: ->(_path) { raise "stubbed failure" })
      end
      assert File.exist?(keychain)
      assert File.exist?(keychain_marker)
      refute File.exist?(installed_profile)
      refute File.exist?(profile_marker)
      refute File.exist?(ios_output)

      ReleaseContract.cleanup_ios!(
        environment,
        repository,
        delete_keychain: lambda do |path|
          FileUtils.rm_f(path)
          true
        end
      )
      refute File.exist?(keychain)
      refute File.exist?(keychain_marker)
    end
  end

  def test_ios_cleanup_refuses_changed_owned_profile
    Dir.mktmpdir do |directory|
      runner_temp = File.join(directory, "runner")
      home = File.join(directory, "home")
      marker = File.join(runner_temp, "profile-marker")
      source = File.join(runner_temp, "source.mobileprovision")
      installed = File.join(
        home,
        "Library/Developer/Xcode/UserData/Provisioning Profiles/owned.mobileprovision"
      )
      [source, installed].each do |path|
        FileUtils.mkdir_p(File.dirname(path))
        File.binwrite(path, "owned")
      end
      ReleaseContract.write_profile_marker(marker, source, installed, runner_temp)
      File.binwrite(installed, "replaced")

      assert_raises(ReleaseContract::Error) do
        ReleaseContract.cleanup_ios!(
          {
            "HOME" => home,
            "RUNNER_TEMP" => runner_temp,
            "IOS_PROFILE_MARKER_PATH" => marker
          },
          File.join(directory, "repository")
        )
      end
      assert_equal "replaced", File.binread(installed)
      assert File.exist?(marker)
    end
  end

  def test_cleanup_removes_only_release_owned_files
    Dir.mktmpdir do |directory|
      repository = File.join(directory, "repository")
      runner_temp = File.join(directory, "runner")
      home = File.join(directory, "home")
      signing_directory = File.join(runner_temp, "vlrgg-signing.test")
      keystore = File.join(signing_directory, "release.keystore")
      android_output = File.join(repository, "app/androidApp/build/outputs/bundle/release/app.aab")
      ios_output = File.join(repository, "app/iosApp/build/export/app.ipa")
      outside = File.join(directory, "keep")
      [keystore, android_output, ios_output, outside].each do |path|
        FileUtils.mkdir_p(File.dirname(path))
        File.write(path, "test")
      end

      ReleaseContract.cleanup_android!(
        {
          "ANDROID_KEYSTORE_PATH" => keystore,
          "ANDROID_SIGNING_TEMP_DIR" => signing_directory,
          "RUNNER_TEMP" => runner_temp
        },
        repository
      )
      ReleaseContract.cleanup_ios!(
        { "HOME" => home, "RUNNER_TEMP" => runner_temp },
        repository
      )

      refute File.exist?(keystore)
      refute File.exist?(android_output)
      refute File.exist?(ios_output)
      assert File.exist?(outside)
    end
  end

  def test_android_cleanup_requires_owned_signing_directory
    Dir.mktmpdir do |directory|
      runner_temp = File.join(directory, "runner")
      owned_directory = File.join(runner_temp, "vlrgg-signing.owned")
      owned_keystore = File.join(owned_directory, "upload.keystore")
      existing_keystore = File.join(runner_temp, "existing.keystore")
      [owned_keystore, existing_keystore].each do |path|
        FileUtils.mkdir_p(File.dirname(path))
        File.binwrite(path, "original")
      end

      ReleaseContract.cleanup_android!(
        {
          "ANDROID_KEYSTORE_PATH" => existing_keystore,
          "RUNNER_TEMP" => runner_temp
        },
        File.join(directory, "repository")
      )
      assert_equal "original", File.binread(existing_keystore)

      ReleaseContract.cleanup_android!(
        {
          "ANDROID_KEYSTORE_PATH" => owned_keystore,
          "ANDROID_SIGNING_TEMP_DIR" => owned_directory,
          "RUNNER_TEMP" => runner_temp
        },
        File.join(directory, "repository")
      )
      refute File.exist?(owned_directory)
    end
  end

  def test_deploy_workflows_are_manual_and_keep_secrets_behind_environments
    workflows = %w[deploy-app-android.yml deploy-app-ios.yml].each_with_index.map do |name, index|
      path = File.join(ROOT, ".github/workflows", name)
      document = YAML.load_file(path)
      assert_equal ["workflow_dispatch"], document.fetch(true).keys
      assert_equal({ "actions" => "read", "contents" => "read" }, document.fetch("permissions"))
      assert_equal %w[android-internal ios-testflight][index], document.fetch("jobs").fetch("deploy").fetch("environment")
      refute document.fetch("jobs").fetch("deploy").fetch("env").values.any? { |value| value.include?("secrets.") },
             "Deployment secrets must be scoped to consuming steps."
      File.read(path)
    end

    workflows.each do |workflow|
      assert_includes workflow, "persist-credentials: false"
      assert_includes workflow, "needs.preflight.outputs.source_sha"
      refute_includes workflow, "upload-artifact"
      assert_includes workflow, "if: always()"
    end
    assert_includes workflows[0], 'mktemp -d "$RUNNER_TEMP/vlrgg-signing.XXXXXX"'
    assert_includes workflows[0], "ANDROID_SIGNING_TEMP_DIR"
    assert_includes workflows[1], "runs-on: macos-26"
    assert_includes workflows[1], "/Applications/Xcode_26.6.app/Contents/Developer"
    refute_includes workflows[1], "IOS_PROVISIONING_PROFILE_NAME"

    fastfile = File.read(File.join(ROOT, "fastlane/Fastfile"))
    assert_includes fastfile, "ReleaseContract.validate_workflow_source!"
    assert_includes fastfile, "ReleaseContract.ensure_absent!(installed_profile"
    refute_includes File.read(File.join(ROOT, "scripts/app-release/release_contract.rb")), ".appstoreconnect/private_keys"
  end

  private

  def valid_environment
    {
      "APP_VERSION" => "1.2.3",
      "APP_BUILD_NUMBER" => "42",
      "SOURCE_SHA" => "a" * 40
    }
  end
end
