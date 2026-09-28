# frozen_string_literal: true

require "json"
require "open3"

module CiContract
  class Error < StandardError; end

  TARGETS = %w[android server ios].freeze
  SHA = /\A[0-9a-f]{40}\z/

  module_function

  def affected(paths)
    paths.flat_map do |path|
      case path
      when /\A(?:README|AGENTS|DESIGN)\.md\z/, /\Adocs\/.*\.md\z/,
           /\A(?:server|core|app\/(?:androidApp|shared|iosApp))\/AGENTS\.md\z/
        []
      # Gradle configures all included projects, including the app build scripts in Docker.
      when /(?:\A|\/)(?:build|settings)\.gradle(?:\.kts)?\z/
        TARGETS
      when /\Aserver\/src\// then ["server"]
      when /\Aapp\/androidApp\/(?:src|scripts)\// then ["android"]
      when /\Aapp\/iosApp\// then ["ios"]
      when /\Aapp\/shared\/src\// then %w[android ios]
      else TARGETS
      end
    end.uniq
  end

  def git!(repo, *arguments)
    output, _error, status = Open3.capture3("git", "-C", repo, *arguments)
    raise Error, "Git change range is unavailable." unless status.success?

    output
  end

  def change_range(event, environment, repo)
    case environment.fetch("GITHUB_EVENT_NAME")
    when "pull_request"
      base = event.fetch("pull_request").fetch("base").fetch("sha")
      head = event.fetch("pull_request").fetch("head").fetch("sha")
    when "push"
      base, head = event.fetch("before"), event.fetch("after")
      raise Error, "Push identity does not match the checkout." unless
        environment["GITHUB_REF"] == "refs/heads/main" && head == environment["GITHUB_SHA"]
    else
      raise Error, "No automatic change range for this event."
    end
    raise Error, "Invalid change range identity." unless [base, head].all? { |sha| sha.is_a?(String) && SHA.match?(sha) }
    raise Error, "Checkout does not match the event head." unless git!(repo, "rev-parse", "HEAD").strip == environment.fetch("GITHUB_SHA")

    git!(repo, "cat-file", "-e", "#{base}^{commit}")
    base = git!(repo, "merge-base", base, head).strip if environment["GITHUB_EVENT_NAME"] == "pull_request"
    [base, head]
  end

  def changed_paths(event, environment, repo)
    # --no-renames retains both removed and added paths, including cross-platform moves.
    git!(repo, "diff", "--no-renames", "--name-only", "-z", *change_range(event, environment, repo), "--").split("\0")
  end

  def detect(event, environment = ENV, repo = ".")
    if environment["GITHUB_EVENT_NAME"] == "workflow_dispatch"
      target = event.fetch("inputs").fetch("target")
      source = event.fetch("inputs").fetch("source_sha")
      raise Error, "Manual CI requires the exact main SHA and a valid target." unless
        (TARGETS + ["all"]).include?(target) && SHA.match?(source) &&
        environment["GITHUB_REF"] == "refs/heads/main" && source == environment["GITHUB_SHA"] &&
        git!(repo, "rev-parse", "HEAD").strip == source

      return target == "all" ? TARGETS : [target]
    end

    begin
      affected(changed_paths(event, environment, repo))
    rescue Error, KeyError, TypeError, NoMethodError, Errno::ENOENT
      warn "Change selection is uncertain; running all platforms."
      TARGETS
    end
  end

  def check_whitespace!(event, environment = ENV, repo = ".")
    # Dispatch validates an existing exact commit; it introduces no patch to whitespace-check.
    if environment["GITHUB_EVENT_NAME"] == "workflow_dispatch"
      detect(event, environment, repo)
      return true
    end

    range = begin
      change_range(event, environment, repo)
    rescue Error, KeyError, TypeError, NoMethodError
      # Initial pushes and unavailable ranges inspect the entire checked-out tree.
      [git!(repo, "hash-object", "-t", "tree", "/dev/null").strip, "HEAD"]
    end
    git!(repo, "diff", "--check", *range, "--")
    true
  end

  def verify_gate!(needs)
    raise Error, "CI change detection did not succeed." unless needs.fetch("changes").fetch("result") == "success"

    TARGETS.each do |target|
      selected = needs.fetch("changes").fetch("outputs").fetch(target)
      expected = { "true" => "success", "false" => "skipped" }.fetch(selected)
      raise Error, "CI #{target} did not finish as selected (expected #{expected})." unless needs.fetch(target).fetch("result") == expected
    end
    true
  rescue KeyError, TypeError, NoMethodError
    raise Error, "CI selection or job results are incomplete."
  end

  def github_response(path)
    output, _error, status = Open3.capture3("gh", "api", "--method", "GET", path)
    raise Error, "The GitHub CI lookup failed." unless status.success?

    JSON.parse(output)
  rescue JSON::ParserError, Errno::ENOENT
    raise Error, "The GitHub CI lookup failed."
  end

  # Final deployment authorization runs inside the shared platform job concurrency group.
  # This read verifies the latest proof; the job lock prevents concurrent same-platform CI execution.
  def verify_platform!(target, source_sha, repository, api: method(:github_response))
    raise Error, "Invalid CI target, source SHA, or repository." unless
      TARGETS.include?(target) && SHA.match?(source_sha) && /\A[A-Za-z0-9_.-]+\/[A-Za-z0-9_.-]+\z/.match?(repository)

    expected = { "head_sha" => source_sha, "head_branch" => "main", "path" => ".github/workflows/ci.yml" }
    # The newest exact-SHA main run wins, including manual validation. Never reuse an older green run.
    runs_path = "repos/#{repository}/actions/workflows/ci.yml/runs?branch=main&head_sha=#{source_sha}&per_page=100"
    read_run = lambda do
      response = api.call(runs_path)
      runs = response.fetch("workflow_runs")
      unless runs.is_a?(Array) && runs.length <= 100 && response.fetch("total_count") == runs.length && runs.all? { |run|
        run.is_a?(Hash) && %w[id run_number run_attempt].all? { |key| run[key].is_a?(Integer) && run[key].positive? }
      } && %w[id run_number].all? { |key| runs.map { |run| run[key] }.uniq.length == runs.length }
        raise Error, "The CI run inventory was malformed or incomplete."
      end
      run = runs.max_by { |candidate| candidate.fetch("run_number") }
      unless run && expected.all? { |key, value| run[key] == value } && %w[push workflow_dispatch].include?(run["event"])
        raise Error, "The frozen source SHA has no matching main CI run."
      end
      run.slice(*expected.keys, "event", "id", "run_number", "run_attempt")
    end

    run = read_run.call
    response = api.call("repos/#{repository}/actions/runs/#{run.fetch('id')}/attempts/#{run.fetch('run_attempt')}/jobs?per_page=100")
    jobs = response.fetch("jobs")
    unless jobs.is_a?(Array) && jobs.length <= 100 && jobs.all? { |job| job.is_a?(Hash) } && response.fetch("total_count") == jobs.length
      raise Error, "The CI job inventory was malformed or incomplete."
    end
    selected = jobs.select { |job| job["name"] == target }
    expected_job = { "run_id" => run.fetch("id"), "run_attempt" => run.fetch("run_attempt"), "head_sha" => source_sha,
                     "status" => "completed", "conclusion" => "success" }
    unless selected.length == 1 && expected_job.all? { |key, value| selected.first[key] == value }
      raise Error, "The latest main CI attempt has no successful #{target} job; run CI manually for this exact SHA and target."
    end
    raise Error, "The main CI run changed during verification; retry after #{target} succeeds." unless read_run.call == run

    true
  rescue KeyError, TypeError, NoMethodError
    raise Error, "The CI response was malformed."
  end
end

if $PROGRAM_NAME == __FILE__
  begin
    case ARGV.shift
    when "detect"
      selected = CiContract.detect(JSON.parse(File.read(ENV.fetch("GITHUB_EVENT_PATH"))))
      File.open(ENV.fetch("GITHUB_OUTPUT"), "a") do |output|
        CiContract::TARGETS.each { |target| output.puts "#{target}=#{selected.include?(target)}" }
      end
    when "gate"
      CiContract.verify_gate!(JSON.parse(ENV.fetch("CI_NEEDS")))
    when "whitespace"
      CiContract.check_whitespace!(JSON.parse(File.read(ENV.fetch("GITHUB_EVENT_PATH"))))
    when "verify"
      CiContract.verify_platform!(*ARGV)
    else
      raise CiContract::Error, "Unknown CI contract command."
    end
  rescue CiContract::Error, JSON::ParserError, KeyError, TypeError => error
    warn error.message
    exit 1
  end
end
