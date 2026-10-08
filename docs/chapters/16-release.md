# Chapter 16: Packaging, CI and supported hosts

Status: Pending

Plan milestone: 8. Dependencies: 15.

## Goal

Produce a verified installable release with reproducible checks and accurate documentation.

## Work

Add CI with build/tests/project checks, explicit verifier host matrix and artifacts, no Marketplace auto-publication. Finish README/compiler setup/workflow/limits and notices. Inspect ZIP for duplicate Kotlin/coroutines or legacy runtimes; declare reviewed minimum and only verified support.

## Acceptance

Run check, verifyPluginProjectConfiguration, verifyPluginStructure, buildPlugin, verifyPlugin; fresh WS ZIP installation and IDEA+JavaScript verification. Record target and intended current-stable versions actually tested. All release gates must pass before marking complete.

## Execution evidence

Not yet executed.
