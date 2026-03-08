#!/bin/bash
# QuarkAI Build Verification Script
# Purpose: Verify all modules build, tests pass, and generate coverage reports

set -e  # Exit on any error

echo "=================================================="
echo "QuarkAI Build Verification"
echo "=================================================="
echo ""

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[$(date +%H:%M:%S)]${NC} $1"
}

print_success() {
    echo -e "${GREEN}✓${NC} $1"
}

print_error() {
    echo -e "${RED}✗${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}⚠${NC} $1"
}

# Track timing
START_TIME=$(date +%s)

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Phase 1: Clean Build
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

print_status "Phase 1: Clean Build"
echo "Running: mvn clean package -DskipTests"
echo ""

if mvn clean package -DskipTests; then
    print_success "Clean build completed successfully"
else
    print_error "Clean build failed!"
    exit 1
fi

echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Phase 2: Unit Tests
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

print_status "Phase 2: Running Unit Tests"
echo "Running: mvn test"
echo ""

if mvn test; then
    print_success "All unit tests passed"
else
    print_error "Unit tests failed!"
    exit 1
fi

echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Phase 3: Code Coverage Report
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

print_status "Phase 3: Generating Code Coverage Reports"
echo "Running: mvn jacoco:report"
echo ""

if mvn jacoco:report; then
    print_success "Coverage reports generated"
    echo ""
    print_warning "Coverage reports available at:"
    echo "  - quarkai-core/target/site/jacoco/index.html"
    echo "  - quarkai-openai/target/site/jacoco/index.html"
    echo "  - quarkai-anthropic/target/site/jacoco/index.html"
    echo "  - quarkai-vertex/target/site/jacoco/index.html"
    echo "  - quarkai-ollama/target/site/jacoco/index.html"
    echo "  - quarkai-rag/target/site/jacoco/index.html"
    echo "  - quarkai-vertx/target/site/jacoco/index.html"
else
    print_warning "Coverage report generation failed (non-critical)"
fi

echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Phase 4: Module Verification
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

print_status "Phase 4: Verifying Module Artifacts"
echo ""

MODULES=(
    "quarkai-core"
    "quarkai-openai"
    "quarkai-anthropic"
    "quarkai-vertex"
    "quarkai-ollama"
    "quarkai-rag"
    "quarkai-vertx"
    "quarkai-quarkus-extension/runtime"
    "quarkai-quarkus-extension/deployment"
)

ALL_ARTIFACTS_EXIST=true

for module in "${MODULES[@]}"; do
    JAR_PATH="$module/target/*.jar"
    if ls $JAR_PATH 1> /dev/null 2>&1; then
        print_success "Module artifact exists: $module"
    else
        print_error "Module artifact missing: $module"
        ALL_ARTIFACTS_EXIST=false
    fi
done

echo ""

if [ "$ALL_ARTIFACTS_EXIST" = true ]; then
    print_success "All module artifacts verified"
else
    print_error "Some module artifacts are missing!"
    exit 1
fi

echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Phase 5: Test Summary
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

print_status "Phase 5: Test Summary"
echo ""

echo "Counting test results..."
TOTAL_TESTS=0
FAILED_TESTS=0

for module in "${MODULES[@]}"; do
    if [ -f "$module/target/surefire-reports/TEST-*.xml" ]; then
        MODULE_TESTS=$(grep -oP 'tests="\K[0-9]+' $module/target/surefire-reports/TEST-*.xml 2>/dev/null | awk '{s+=$1} END {print s}')
        MODULE_FAILURES=$(grep -oP 'failures="\K[0-9]+' $module/target/surefire-reports/TEST-*.xml 2>/dev/null | awk '{s+=$1} END {print s}')

        if [ -n "$MODULE_TESTS" ]; then
            TOTAL_TESTS=$((TOTAL_TESTS + MODULE_TESTS))
            FAILED_TESTS=$((FAILED_TESTS + MODULE_FAILURES))
            echo "  $module: $MODULE_TESTS tests, $MODULE_FAILURES failures"
        fi
    fi
done

echo ""
print_success "Total Tests: $TOTAL_TESTS"
if [ $FAILED_TESTS -eq 0 ]; then
    print_success "Failed Tests: $FAILED_TESTS"
else
    print_error "Failed Tests: $FAILED_TESTS"
fi

echo ""

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Final Summary
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

END_TIME=$(date +%s)
DURATION=$((END_TIME - START_TIME))

echo "=================================================="
echo "Build Verification Complete!"
echo "=================================================="
echo ""
echo "Time taken: ${DURATION} seconds"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    print_success "✓ All checks passed - QuarkAI is production ready!"
    echo ""
    echo "Next steps:"
    echo "  1. Review coverage reports"
    echo "  2. Test native image compilation (optional)"
    echo "  3. Deploy to staging environment"
    exit 0
else
    print_error "✗ Some tests failed - please review the output above"
    exit 1
fi
