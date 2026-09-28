# frozen_string_literal: true

require "minitest/autorun"
require "tmpdir"
require "fileutils"
require "yaml"
require_relative "ci_contract"

class CiContractTest < Minitest::Test
  ROOT = File.expand_path("../..", __dir__)
  SHA = "b" * 40

  def test_single_conservative_path_map
    cases = {
      "server/src/main/App.kt" => %w[server], "server/src/test/Test.kt" => %w[server],
      "app/androidApp/src/main/Main.kt" => %w[android], "app/androidApp/scripts/check.sh" => %w[android],
      "app/iosApp/iosApp/App.swift" => %w[ios], "app/iosApp/Scripts/check.sh" => %w[ios],
      "app/shared/src/commonMain/Shared.kt" => %w[android ios],
      "app/shared/src/androidMain/Platform.kt" => %w[android ios],
      "core/src/commonMain/Core.kt" => CiContract::TARGETS,
      "app/androidApp/build.gradle.kts" => CiContract::TARGETS,
      "app/shared/build.gradle.kts" => CiContract::TARGETS,
      "server/build.gradle.kts" => CiContract::TARGETS,
      "settings.gradle.kts" => CiContract::TARGETS, "gradle/libs.versions.toml" => CiContract::TARGETS,
      "gradlew" => CiContract::TARGETS, "gradle.properties" => CiContract::TARGETS,
      "Dockerfile" => CiContract::TARGETS, ".github/workflows/ci.yml" => CiContract::TARGETS,
      "scripts/ci/ci_contract.rb" => CiContract::TARGETS, "scripts/firebase/with_config.py" => CiContract::TARGETS,
      ".github/scripts/smoke-observability.sh" => CiContract::TARGETS,
      "new/build.sh" => CiContract::TARGETS, "docs/build.sh" => CiContract::TARGETS,
      "app/shared/build-logic/plugin.kt" => CiContract::TARGETS,
      "README.md" => [], "docs/ci-cd.md" => [], "server/AGENTS.md" => []
    }
    cases.each { |path, expected| assert_equal expected.sort, CiContract.affected([path]).sort, path }
    assert_equal %w[android server], CiContract.affected(%w[server/src/main/App.kt app/androidApp/src/Main.kt]).sort
    assert_equal [], CiContract.affected([])
  end

  def test_full_push_and_pr_ranges_with_renames_deletions_and_fallback
    Dir.mktmpdir do |repo|
      git(repo, "init", "--quiet")
      commit(repo, "README.md")
      base = git(repo, "rev-parse", "HEAD")
      commit(repo, "server/src/main/Server.kt")
      middle = git(repo, "rev-parse", "HEAD")
      commit(repo, "app/shared/src/commonMain/Shared.kt")
      head = git(repo, "rev-parse", "HEAD")
      env = { "GITHUB_EVENT_NAME" => "push", "GITHUB_SHA" => head, "GITHUB_REF" => "refs/heads/main" }
      event = { "before" => base, "after" => head }
      assert_equal CiContract::TARGETS.sort, CiContract.detect(event, env, repo).sort
      assert_equal %w[android ios], CiContract.detect(event.merge("before" => middle), env, repo).sort
      pr = { "pull_request" => { "base" => { "sha" => base }, "head" => { "sha" => head } } }
      assert_equal CiContract::TARGETS.sort, CiContract.detect(pr, env.merge("GITHUB_EVENT_NAME" => "pull_request"), repo).sort
      git(repo, "checkout", "--quiet", "--detach", base)
      commit(repo, "app/androidApp/src/BaseOnly.kt")
      divergent_base = git(repo, "rev-parse", "HEAD")
      git(repo, "checkout", "--quiet", "--detach", head)
      pr["pull_request"]["base"]["sha"] = divergent_base
      git(repo, "-c", "user.name=CI", "-c", "user.email=ci@example.invalid", "merge", "--quiet", "--no-ff", divergent_base, "-m", "merge fixture")
      merge_sha = git(repo, "rev-parse", "HEAD")
      assert_equal ["app/shared/src/commonMain/Shared.kt", "server/src/main/Server.kt"],
                   CiContract.changed_paths(pr, env.merge("GITHUB_EVENT_NAME" => "pull_request", "GITHUB_SHA" => merge_sha), repo).sort
      FileUtils.mkdir_p(File.join(repo, "docs"))
      git(repo, "mv", "server/src/main/Server.kt", "docs/Server.md")
      git(repo, "rm", "app/shared/src/commonMain/Shared.kt")
      commit(repo, "docs/note.md")
      changed = git(repo, "rev-parse", "HEAD")
      assert_equal CiContract::TARGETS.sort, CiContract.detect({ "before" => head, "after" => changed }, env.merge("GITHUB_SHA" => changed), repo).sort
      assert_equal CiContract::TARGETS.sort, CiContract.detect({ "before" => "0" * 40, "after" => changed }, env.merge("GITHUB_SHA" => changed), repo).sort
      assert_equal CiContract::TARGETS.sort, CiContract.detect(event.merge("after" => base), env, repo).sort
      assert_equal CiContract::TARGETS.sort, CiContract.detect({}, env, repo).sort
      assert CiContract.check_whitespace!(event, env, repo)
      File.write(File.join(repo, "docs/note.md"), "trailing space \n")
      git(repo, "add", ".")
      git(repo, "-c", "user.name=CI", "-c", "user.email=ci@example.invalid", "commit", "--quiet", "-m", "space")
      after = git(repo, "rev-parse", "HEAD")
      assert_raises(CiContract::Error) { CiContract.check_whitespace!({ "before" => changed, "after" => after }, env.merge("GITHUB_SHA" => after), repo) }
    end
  end

  def test_manual_validation_is_bound_to_main_and_exact_sha
    Dir.mktmpdir do |repo|
      git(repo, "init", "--quiet")
      commit(repo, "README.md")
      File.write(File.join(repo, "README.md"), "historical whitespace \n")
      git(repo, "add", ".")
      git(repo, "-c", "user.name=CI", "-c", "user.email=ci@example.invalid", "commit", "--quiet", "-m", "history")
      sha = git(repo, "rev-parse", "HEAD")
      env = { "GITHUB_EVENT_NAME" => "workflow_dispatch", "GITHUB_REF" => "refs/heads/main", "GITHUB_SHA" => sha }
      (CiContract::TARGETS + ["all"]).each do |target|
        selected = CiContract.detect({ "inputs" => { "target" => target, "source_sha" => sha } }, env, repo)
        assert_equal(target == "all" ? CiContract::TARGETS : [target], selected)
        assert CiContract.check_whitespace!({ "inputs" => { "target" => target, "source_sha" => sha } }, env, repo)
      end
      assert_raises(CiContract::Error) { CiContract.detect({ "inputs" => { "target" => "server", "source_sha" => SHA } }, env, repo) }
      assert_raises(CiContract::Error) { CiContract.detect({ "inputs" => { "target" => "server", "source_sha" => sha } }, env.merge("GITHUB_REF" => "refs/heads/feature"), repo) }
      assert_raises(CiContract::Error) { CiContract.detect({ "inputs" => { "target" => "invalid", "source_sha" => sha } }, env, repo) }
    end
  end

  def test_final_gate_rejects_failed_detector_selected_failures_and_unexpected_skips
    needs = { "changes" => { "result" => "success", "outputs" => { "android" => "false", "server" => "true", "ios" => "false" } },
              "android" => { "result" => "skipped" }, "server" => { "result" => "success" }, "ios" => { "result" => "skipped" } }
    assert CiContract.verify_gate!(needs)
    %w[failure cancelled skipped].each do |result|
      bad = Marshal.load(Marshal.dump(needs))
      bad["server"]["result"] = result
      assert_raises(CiContract::Error) { CiContract.verify_gate!(bad) }
      bad = Marshal.load(Marshal.dump(needs))
      bad["changes"]["result"] = result
      assert_raises(CiContract::Error) { CiContract.verify_gate!(bad) }
    end
    all_skipped = { "changes" => { "result" => "success", "outputs" => CiContract::TARGETS.to_h { |target| [target, "false"] } } }
    CiContract::TARGETS.each { |target| all_skipped[target] = { "result" => "skipped" } }
    assert CiContract.verify_gate!(all_skipped)
    needs["changes"]["outputs"].delete("ios")
    assert_raises(CiContract::Error) { CiContract.verify_gate!(needs) }
  end

  def test_each_platform_requires_its_actual_latest_attempt_success
    CiContract::TARGETS.each do |target|
      %w[push workflow_dispatch].each do |event|
        run, jobs = responses(target)
        run["event"] = event
        paths = []
        api = lambda do |path|
          paths << path
          path.include?("/attempts/") ? jobs : { "total_count" => 2, "workflow_runs" => [run.merge("id" => 84, "run_number" => 6), run] }
        end
        assert CiContract.verify_platform!(target, SHA, "owner/repository", api: api)
        assert_equal paths.first, paths.last
        assert_includes paths[1], "/runs/83/attempts/2/jobs?per_page=100"
      end
    end
  end

  def test_platform_proof_rejects_stale_malformed_incomplete_duplicate_and_unsuccessful_evidence
    mutations = [
      ->(run, _) { run["head_sha"] = "c" * 40 }, ->(run, _) { run["head_branch"] = "feature" },
      ->(run, _) { run["event"] = "pull_request" }, ->(run, _) { run["path"] = ".github/workflows/other.yml" },
      ->(run, _) { run["id"] = "83" }, ->(run, _) { run["run_number"] = 0 }, ->(run, _) { run["run_attempt"] = 0 },
      ->(_, jobs) { jobs["jobs"][0]["head_sha"] = "c" * 40 }, ->(_, jobs) { jobs["jobs"][0]["run_id"] = 82 },
      ->(_, jobs) { jobs["jobs"][0]["run_attempt"] = 1 }, ->(_, jobs) { jobs["jobs"][0].delete("run_attempt") },
      ->(_, jobs) { jobs["jobs"][0]["status"] = "in_progress" },
      *%w[failure skipped cancelled].map { |result| ->(_, jobs) { jobs["jobs"][0]["conclusion"] = result } },
      ->(_, jobs) { jobs["jobs"][0]["name"] = "verify" },
      ->(_, jobs) { jobs["jobs"] = [jobs["jobs"][0], jobs["jobs"][0]] },
      ->(_, jobs) { jobs["total_count"] = 101 }, ->(_, jobs) { jobs["jobs"] = nil }
    ]
    CiContract::TARGETS.each do |target|
      mutations.each do |mutate|
        run, jobs = responses(target)
        mutate.call(run, jobs)
        api = ->(path) { path.include?("/attempts/") ? jobs : { "total_count" => 1, "workflow_runs" => [run] } }
        assert_raises(CiContract::Error) { CiContract.verify_platform!(target, SHA, "owner/repository", api: api) }
      end
    end
    [nil, {}, { "total_count" => 0, "workflow_runs" => [] },
     { "total_count" => 101, "workflow_runs" => [responses("server").first] },
     { "total_count" => 2, "workflow_runs" => [responses("server").first] * 2 }].each do |response|
      assert_raises(CiContract::Error) { CiContract.verify_platform!("server", SHA, "owner/repository", api: ->(_) { response }) }
    end
  end

  def test_newer_unrelated_manual_run_cannot_reuse_older_push_success
    run, jobs = responses("server")
    old = run.merge("id" => 82, "run_number" => 6, "event" => "push", "status" => "completed", "conclusion" => "success")
    run["event"] = "workflow_dispatch"
    %w[skipped cancelled failure].each do |conclusion|
      jobs["jobs"][0]["conclusion"] = conclusion
      api = ->(path) { path.include?("/attempts/") ? jobs : { "total_count" => 2, "workflow_runs" => [old, run] } }
      assert_raises(CiContract::Error) { CiContract.verify_platform!("server", SHA, "owner/repository", api: api) }
    end
  end

  def test_new_run_or_attempt_during_proof_is_rejected
    %w[id run_number run_attempt].each do |field|
      run, jobs = responses("server")
      reads = 0
      api = lambda do |path|
        next jobs if path.include?("/attempts/")
        reads += 1
        { "total_count" => 1, "workflow_runs" => [reads == 1 ? run : run.merge(field => run.fetch(field) + 1)] }
      end
      assert_raises(CiContract::Error) { CiContract.verify_platform!("server", SHA, "owner/repository", api: api) }
    end
  end

  def test_apps_recheck_after_environment_wait_before_using_deployment_credentials
    %w[android ios].each do |target|
      run, jobs = responses(target)
      api = ->(path) { path.include?("/attempts/") ? jobs : { "total_count" => 1, "workflow_runs" => [run] } }
      assert CiContract.verify_platform!(target, SHA, "owner/repository", api: api) # Early preflight.
      run["run_attempt"] += 1
      jobs["jobs"][0]["run_attempt"] = run["run_attempt"]
      [["in_progress", nil], ["completed", "failure"]].each do |status, conclusion|
        jobs["jobs"][0].merge!("status" => status, "conclusion" => conclusion)
        assert_raises(CiContract::Error) { CiContract.verify_platform!(target, SHA, "owner/repository", api: api) }
      end

      workflow = YAML.load_file(File.join(ROOT, ".github/workflows/deploy-app-#{target}.yml"))
      deploy = workflow.fetch("jobs").fetch("deploy")
      assert_equal "preflight", deploy.fetch("needs")
      assert_equal(target == "android" ? "android-internal" : "ios-testflight", deploy.fetch("environment"))
      assert_equal "read", deploy.fetch("permissions", workflow.fetch("permissions")).fetch("actions")
      steps = deploy.fetch("steps")
      verification = steps.index { |step| step["run"] == 'ruby scripts/ci/ci_contract.rb verify ' + target + ' "$SOURCE_SHA" "$GITHUB_REPOSITORY"' }
      refute_nil verification, "#{target} must recheck CI after the protected environment wait"
      ruby_setup = steps.index { |step| step["uses"].to_s.start_with?("ruby/setup-ruby@") }
      credentials = steps.index { |step| step["uses"].to_s.start_with?("google-github-actions/auth@") || step["run"].to_s.include?("fastlane ios internal") }
      assert_operator verification, :>, ruby_setup
      assert_operator verification, :<, credentials
      assert_equal "${{ github.token }}", steps[verification].fetch("env").fetch("GH_TOKEN")
    end
  end

  def test_workflows_use_platform_proof_and_unconditional_final_gate
    %w[android ios server].each do |target|
      path = target == "server" ? "deploy-server.yml" : "deploy-app-#{target}.yml"
      source = File.read(File.join(ROOT, ".github/workflows", path))
      assert_includes source, "ruby scripts/ci/ci_contract.rb verify #{target}"
      refute_includes source, "verify-android-ci"
      refute_includes source, 'select(.name == "verify")'
    end
    workflow = YAML.load_file(File.join(ROOT, ".github/workflows/ci.yml"))
    assert_equal %w[pull_request push workflow_dispatch], workflow.fetch(true).keys
    jobs = workflow.fetch("jobs")
    assert_equal "always()", jobs.fetch("verify").fetch("if")
    assert_equal %w[changes android server ios].sort, jobs.fetch("verify").fetch("needs").sort
    # Retain GitHub's synthetic PR merge checkout for build integration coverage.
    jobs.each_value do |job|
      checkout = job.fetch("steps").find { |step| step["uses"].to_s.start_with?("actions/checkout@") }
      refute checkout.fetch("with").key?("ref")
    end
    CiContract::TARGETS.each do |target|
      assert_includes jobs.fetch(target).fetch("if"), "needs.changes.outputs.#{target} == 'true'"
    end
    assert_equal "ubuntu-latest", jobs.fetch("server").fetch("runs-on")
    assert_equal "ubuntu-latest", jobs.fetch("android").fetch("runs-on")
    assert_equal "macos-26", jobs.fetch("ios").fetch("runs-on")
  end

  private

  def responses(target)
    run = { "id" => 83, "run_number" => 7, "run_attempt" => 2, "head_sha" => SHA, "head_branch" => "main",
            "event" => "push", "path" => ".github/workflows/ci.yml", "status" => "in_progress", "conclusion" => nil }
    job = { "id" => 1, "run_id" => 83, "run_attempt" => 2, "head_sha" => SHA, "name" => target,
            "status" => "completed", "conclusion" => "success" }
    [run, { "total_count" => 2, "jobs" => [job, job.merge("id" => 2, "name" => "verify", "status" => "in_progress", "conclusion" => nil)] }]
  end

  def git(repo, *args)
    output, error, status = Open3.capture3("git", "-C", repo, *args)
    assert status.success?, error
    output.strip
  end

  def commit(repo, path)
    FileUtils.mkdir_p(File.dirname(File.join(repo, path)))
    File.write(File.join(repo, path), "fixture\n")
    git(repo, "add", ".")
    git(repo, "-c", "user.name=CI", "-c", "user.email=ci@example.invalid", "commit", "--quiet", "-m", "fixture")
  end
end
