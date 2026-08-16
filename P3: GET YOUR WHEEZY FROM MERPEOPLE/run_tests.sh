#!/usr/bin/env bash
# run_tests.sh
# Minimal test harness for MiniJava -> microIR pipeline + interpreter (pgi.jar)
#
# What it does (simple and strict):
#  - For every tests/*.java, generate IR using `java P3`.
#  - Run microIR via `java -jar pgi.jar` and capture its stdout.
#  - Compare stdout ONLY against tests/<name>.out (no IR diffing).
#  - Print a single PASSED/FAILED line per test and a short summary.
#  - Optional: --update creates missing .out baselines using current output.
#
# Usage:
#   ./run_tests.sh            # run all tests
#   ./run_tests.sh --update   # create baselines for any missing .out
#
# Environment overrides:
#   PGI_JAR   (default: pgi.jar)
#   JAVA_CMD  (default: java)
#   TEST_GLOB (default: tests/*.java)
#
# Requirements: bash, diff, printf

set -u

declare -a failed_tests

TEST_DIR="tests"
PGI_JAR=${PGI_JAR:-pgi.jar}
JAVA_CMD=${JAVA_CMD:-java}

UPDATE_MODE=0
for arg in "$@"; do
  case "$arg" in
    --update)
      UPDATE_MODE=1
      ;;
    *)
      ;;
  esac
done

pass_count=0
fail_count=0
declare -a failed_tests


normalize() {
  sed -e 's/\r$//' -e 's/[[:space:]]*$//'
}

compare_files() {
  # $1 actual, $2 expected
  if diff -u <(normalize < "$1") <(normalize < "$2") > /dev/null 2>&1; then
    return 0
  else
    return 1
  fi
}


run_single_test() {
  local java_file="$1"
  local base
  base=$(basename "$java_file" .java)
  local ir_file="${base}_IR.miniIR"
  local actual_out="${base}.actual.out"
  local expected_out="${TEST_DIR}/${base}.out"

  printf "[TEST] %s: " "$base"

  # 1. Generate IR
  if ! $JAVA_CMD P3 < "$java_file" > "$ir_file" 2>"${base}.error"; then
    echo "FAILED (emit error, see ${base}.error)"
    fail_count=$((fail_count+1))
    failed_tests+=("$base: emit")
    return
  fi

  # 2. Interpret IR (capture output)
  if [[ -f "$PGI_JAR" ]]; then
    if ! $JAVA_CMD -jar "$PGI_JAR" < "$ir_file" > "$actual_out" 2>>"${base}.error"; then
      echo "FAILED (runtime error, see ${base}.error)"
      fail_count=$((fail_count+1))
      failed_tests+=("$base: run")
      return
    fi
  fi

  # 3. Ensure expected output exists (optionally create with --update)
  if [[ ! -f "$expected_out" && $UPDATE_MODE -eq 1 ]]; then
    cp "$actual_out" "$expected_out"
  fi

  # 4. Output comparison ONLY
  if [[ -f "$expected_out" ]] && compare_files "$actual_out" "$expected_out"; then
    echo "PASSED"
    pass_count=$((pass_count+1))
    # Clean up noise when passing
    [[ -s "${base}.error" ]] || rm -f "${base}.error"
    rm -f "$actual_out"
  else
    echo "FAILED"
    fail_count=$((fail_count+1))
    failed_tests+=("$base")
  fi
}


shopt -s nullglob
TEST_GLOB=${TEST_GLOB:-"${TEST_DIR}/*.java"}
java_tests=( $TEST_GLOB )
IFS=$'\n' java_tests=($(printf '%s\n' "${java_tests[@]}" | sort))
unset IFS
shopt -u nullglob

if [[ ${#java_tests[@]} -eq 0 ]]; then
  echo "No .java tests found in $TEST_DIR" >&2
  exit 1
fi

for jf in "${java_tests[@]}"; do
  run_single_test "$jf"
done

echo
echo "Summary: PASS=$pass_count FAIL=$fail_count"
if [[ $fail_count -gt 0 ]]; then
  echo "Failed: ${failed_tests[*]}"
fi

if [[ $fail_count -gt 0 ]]; then
  exit 1
fi
exit 0
