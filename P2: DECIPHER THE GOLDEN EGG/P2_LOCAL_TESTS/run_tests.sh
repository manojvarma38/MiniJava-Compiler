#!/bin/bash

# Compile the Java program
javac P2.java
if [ $? -ne 0 ]; then
    echo "Compilation failed."
    exit 1
fi

# Initialize counters
total=0
passed=0

# Directory for test cases
TESTDIR="testcases"

# Loop through all .in files inside testcases/
for infile in "$TESTDIR"/*.in; do
    # Get the base name (e.g., test1 from test1.in)
    base=$(basename "$infile" .in)
    outfile="$TESTDIR/$base.out"
    actual="$TESTDIR/$base.actual"

    if [ ! -f "$outfile" ]; then
        echo "⚠️  Skipping $infile (no $outfile found)"
        continue
    fi

    echo "Running $base..."
    ((total++))

    # Run Java program with input
    java P2 < "$infile" > "$actual"

    # Compare
    if diff -q "$outfile" "$actual" > /dev/null; then
        echo "$base PASSED"
        ((passed++))
    else
        echo "$base FAILED"
        echo "Expected:"
        cat "$outfile"
        echo "Got:"
        cat "$actual"
    fi
    echo "----------------------------------"
done

# Final summary
echo "Result: $passed / $total test cases passed."
