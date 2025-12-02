#!/bin/bash

# Docker Integration Test Runner
# Runs end-to-end tests against actual Docker Compose deployment using HTTP requests

set -e

API_URL="http://localhost:8080"
TEST_PASSED=0
TEST_FAILED=0

echo "======================================"
echo "Docker Integration Test Runner"
echo "======================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}✗ Docker is not running${NC}"
    echo "Please start Docker Desktop and try again"
    exit 1
fi
echo -e "${GREEN}✓ Docker is running${NC}"

# Check if Docker Compose is available
if ! command -v docker-compose &> /dev/null; then
    echo -e "${RED}✗ docker-compose not found${NC}"
    echo "Please install Docker Compose"
    exit 1
fi
echo -e "${GREEN}✓ docker-compose is available${NC}"

# Check if jq is available
if ! command -v jq &> /dev/null; then
    echo -e "${YELLOW}⚠ jq not found - installing is recommended for better JSON parsing${NC}"
    echo "Install with: brew install jq (macOS) or apt-get install jq (Linux)"
fi
echo ""

# Function to check if services are running
check_services() {
    docker-compose ps --services --filter "status=running" 2>/dev/null | wc -l | tr -d ' '
}

# Parse command line arguments
AUTO_START=false
AUTO_STOP=false
VERBOSE=false
EXCLUDE_API=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --start)
            AUTO_START=true
            shift
            ;;
        --stop)
            AUTO_STOP=true
            shift
            ;;
        --restart)
            AUTO_START=true
            AUTO_STOP=true
            shift
            ;;
        --verbose)
            VERBOSE=true
            shift
            ;;
        --exclude-api)
            EXCLUDE_API=true
            shift
            ;;
        --help|-h)
            echo "Usage: ./run-docker-tests.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --start        Automatically start Docker Compose before tests"
            echo "  --stop         Automatically stop Docker Compose after tests"
            echo "  --restart      Restart Docker Compose (start before, stop after)"
            echo "  --verbose      Show detailed test output"
            echo "  --exclude-api  Start all services except the API (run API separately)"
            echo "  --help, -h     Show this help message"
            echo ""
            echo "Examples:"
            echo "  ./run-docker-tests.sh                      # Run tests (services must be running)"
            echo "  ./run-docker-tests.sh --start              # Start services then run tests"
            echo "  ./run-docker-tests.sh --restart            # Restart services and run tests"
            echo "  ./run-docker-tests.sh --verbose            # Run with verbose output"
            echo "  ./run-docker-tests.sh --exclude-api --start # Start services except API, then run tests"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Run './run-docker-tests.sh --help' for usage"
            exit 1
            ;;
    esac
done

# Set expected services count based on exclude-api flag
if [ "$EXCLUDE_API" = true ]; then
    EXPECTED_SERVICES=4
else
    EXPECTED_SERVICES=5
fi

# Check if services are already running
echo "Checking Docker Compose services..."
running_services=$(check_services)
if [ "$running_services" -ge "$EXPECTED_SERVICES" ]; then
    echo -e "${GREEN}✓ Docker Compose services are running${NC}"
    SERVICES_WERE_RUNNING=true
else
    echo -e "${YELLOW}⚠ Docker Compose services are not running${NC}"
    SERVICES_WERE_RUNNING=false

    if [ "$AUTO_START" = false ]; then
        echo ""
        echo "Docker Compose services need to be running for these tests."
        echo "You can either:"
        echo "  1. Run 'docker-compose up -d' manually"
        echo "  2. Run this script with '--start' flag"
        echo ""
        read -p "Start Docker Compose now? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            echo "Aborting tests."
            exit 1
        fi
        AUTO_START=true
    fi
fi

# Start services if needed
if [ "$AUTO_START" = true ] && [ "$SERVICES_WERE_RUNNING" = false ]; then
    echo ""
    echo "Starting Docker Compose services..."
    if [ "$EXCLUDE_API" = true ]; then
        docker-compose up -d postgres minio rabbitmq ml-service
    else
        docker-compose up -d
    fi

    echo ""
    echo "Waiting for services to be healthy..."
    sleep 10

    # Wait for API health check
    MAX_ATTEMPTS=30
    ATTEMPT=0
    while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
        if curl -s $API_URL/health > /dev/null 2>&1; then
            echo -e "${GREEN}✓ API is ready${NC}"
            break
        fi
        ATTEMPT=$((ATTEMPT + 1))
        echo "Waiting for API... (attempt $ATTEMPT/$MAX_ATTEMPTS)"
        sleep 2
    done

    if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
        echo -e "${RED}✗ API did not become ready in time${NC}"
        echo ""
        echo "Showing API logs:"
        docker-compose logs --tail=50 api
        exit 1
    fi
fi

# Helper function to make API request and check status code
api_request() {
    local method=$1
    local endpoint=$2
    local data=$3
    local token=$4
    local expected_code=${5:-200}

    # Print the curl command to stderr so it's always visible
    if [ "$method" = "GET" ]; then
        if [ -n "$token" ]; then
            echo -e "${BLUE}  → curl -s -w \"\\n%{http_code}\" -H \"Authorization: Bearer $token\" \"$API_URL$endpoint\"${NC}" >&2
            response=$(curl -s -w "\n%{http_code}" -H "Authorization: Bearer $token" "$API_URL$endpoint")
        else
            echo -e "${BLUE}  → curl -s -w \"\\n%{http_code}\" \"$API_URL$endpoint\"${NC}" >&2
            response=$(curl -s -w "\n%{http_code}" "$API_URL$endpoint")
        fi
    else
        if [ -n "$token" ]; then
            echo -e "${BLUE}  → curl -s -w \"\\n%{http_code}\" -X \"$method\" -H \"Authorization: Bearer $token\" -H \"Content-Type: application/json\" -d '$data' \"$API_URL$endpoint\"${NC}" >&2
            response=$(curl -s -w "\n%{http_code}" -X "$method" \
                -H "Authorization: Bearer $token" \
                -H "Content-Type: application/json" \
                -d "$data" \
                "$API_URL$endpoint")
        else
            echo -e "${BLUE}  → curl -s -w \"\\n%{http_code}\" -X \"$method\" -H \"Content-Type: application/json\" -d '$data' \"$API_URL$endpoint\"${NC}" >&2
            response=$(curl -s -w "\n%{http_code}" -X "$method" \
                -H "Content-Type: application/json" \
                -d "$data" \
                "$API_URL$endpoint")
        fi
    fi

    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" -eq "$expected_code" ] 2>/dev/null; then
        echo "$body"
        return 0
    else
        echo -e "${RED}✗ Expected HTTP $expected_code but got $http_code${NC}" >&2
        if [ "$VERBOSE" = true ]; then
            echo "Response: $body" >&2
        fi
        return 1
    fi
}

# Helper function to extract JSON value (simple grep-based, works without jq)
json_value() {
    local json=$1
    local key=$2
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | sed 's/.*"\([^"]*\)"$/\1/' | head -1
}

json_value_number() {
    local json=$1
    local key=$2
    echo "$json" | grep -o "\"$key\"[[:space:]]*:[[:space:]]*[0-9]*" | grep -o '[0-9]*$' | head -1
}

# Test counter
test_count=0
pass_count=0
fail_count=0

# Function to run a test
run_test() {
    local test_name=$1
    test_count=$((test_count + 1))
    echo -e "${BLUE}[TEST $test_count]${NC} $test_name"
}

# Function to assert test result
assert_success() {
    if [ $? -eq 0 ]; then
        pass_count=$((pass_count + 1))
        echo -e "${GREEN}  ✓ PASSED${NC}"
        return 0
    else
        fail_count=$((fail_count + 1))
        echo -e "${RED}  ✗ FAILED${NC}"
        return 1
    fi
}

echo ""
echo "======================================"
echo "Running Docker Integration Tests"
echo "======================================"
echo ""

# Generate unique identifiers for this test run
TIMESTAMP=$(date +%s)
CLIENT_NFC="NFC_TEST_${TIMESTAMP}"
AGENT_USERNAME="agent_test_${TIMESTAMP}"
AGENT_PASSWORD="testpass123"

echo "============================================================"
echo "TEST SUITE: Complete Workflow with Real ML Processing"
echo "============================================================"
echo ""

# ===================================
# TEST 1: Admin Login
# ===================================
run_test "Admin Login"
ADMIN_RESPONSE=$(api_request POST "/auth/login/admin" '{"username":"admin","password":"admin"}' "" 200)
if assert_success; then
    ADMIN_TOKEN=$(json_value "$ADMIN_RESPONSE" "accessToken")
    if [ -n "$ADMIN_TOKEN" ]; then
        echo "  → Admin token obtained"
    else
        echo -e "${RED}  ✗ Failed to extract admin token${NC}"
        fail_count=$((fail_count + 1))
    fi
fi
echo ""

# ===================================
# TEST 2: Create Client
# ===================================
run_test "Create Client (NFC: $CLIENT_NFC)"
CLIENT_CREATE_RESPONSE=$(api_request POST "/admin/clients/new" "{\"name\":\"Docker Test Client\",\"nfc\":\"$CLIENT_NFC\",\"address\":\"123 Docker St\"}" "$ADMIN_TOKEN" 201)
if assert_success; then
    echo "  → Client created successfully"
fi
echo ""

# ===================================
# TEST 3: Create Agent
# ===================================
run_test "Create Agent (Username: $AGENT_USERNAME)"
AGENT_CREATE_RESPONSE=$(api_request POST "/admin/agents/new" "{\"name\":\"Docker Test Agent\",\"username\":\"$AGENT_USERNAME\",\"password\":\"$AGENT_PASSWORD\"}" "$ADMIN_TOKEN" 201)
if assert_success; then
    AGENT_ID=$(json_value_number "$AGENT_CREATE_RESPONSE" "id")
    if [ -n "$AGENT_ID" ]; then
        echo "  → Agent created with ID: $AGENT_ID"
    else
        echo -e "${RED}  ✗ Failed to extract agent ID${NC}"
        fail_count=$((fail_count + 1))
    fi
fi
echo ""

# ===================================
# TEST 4: Client Login
# ===================================
run_test "Client Login"
CLIENT_LOGIN_RESPONSE=$(api_request POST "/auth/login/client" "{\"nfc\":\"$CLIENT_NFC\"}" "" 200)
if assert_success; then
    CLIENT_TOKEN=$(json_value "$CLIENT_LOGIN_RESPONSE" "accessToken")
    if [ -n "$CLIENT_TOKEN" ]; then
        echo "  → Client token obtained"
    else
        echo -e "${RED}  ✗ Failed to extract client token${NC}"
        fail_count=$((fail_count + 1))
    fi
fi
echo ""

# ===================================
# TEST 5: Upload Images (if test images exist)
# ===================================
# List image files in test-images directory
IMAGE_FILES=($(find test-images -type f \( -iname "*.png" -o -iname "*.jpg" -o -iname "*.jpeg" \) 2>/dev/null))
if [ ${#IMAGE_FILES[@]} -eq 0 ]; then
    echo -e "${YELLOW}  ⊘ SKIPPED - No image files found in test-images directory${NC}"
    echo "  Create test-images directory with sample images (.png, .jpg, .jpeg) to test image upload"
else
    # Randomly select one image
    RANDOM_IMAGE=${IMAGE_FILES[$((RANDOM % ${#IMAGE_FILES[@]}))]}
    run_test "Upload Test Image ($RANDOM_IMAGE)"
    if [ -f "$RANDOM_IMAGE" ]; then
        echo -e "${BLUE}  → curl -s -w \"\\n%{http_code}\" -H \"Authorization: Bearer $CLIENT_TOKEN\" -F \"image=@$RANDOM_IMAGE\" \"$API_URL/clients/submissions/new\"${NC}" >&2
        UPLOAD_RESPONSE=$(curl -s -w "\n%{http_code}" \
            -H "Authorization: Bearer $CLIENT_TOKEN" \
            -F "image=@$RANDOM_IMAGE" \
            "$API_URL/clients/submissions/new")
        
        http_code=$(echo "$UPLOAD_RESPONSE" | tail -n1)
        body=$(echo "$UPLOAD_RESPONSE" | sed '$d')
        
        if [ "$http_code" -eq 201 ]; then
            SUBMISSION_ID=$(json_value_number "$body" "id")
            if [ -n "$SUBMISSION_ID" ]; then
                echo -e "${GREEN}  ✓ PASSED${NC}"
                pass_count=$((pass_count + 1))
                echo "  → Submission created with ID: $SUBMISSION_ID"
            else
                echo -e "${RED}  ✗ FAILED - Could not extract submission ID${NC}"
                fail_count=$((fail_count + 1))
            fi
        else
            echo -e "${RED}  ✗ FAILED - HTTP $http_code${NC}"
            fail_count=$((fail_count + 1))
            if [ "$VERBOSE" = true ]; then
                echo "Response: $body"
            fi
        fi
    else
        echo -e "${YELLOW}  ⊘ SKIPPED - Selected image $RANDOM_IMAGE not found${NC}"
    fi
fi
echo ""

# ===================================
# TEST 6: Wait for ML Processing (if submission was created)
# ===================================
if [ -n "$SUBMISSION_ID" ]; then
    run_test "Wait for ML Processing"
    MAX_ATTEMPTS=30
    ATTEMPT=0
    ML_COMPLETED=false

    while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
        if [ $ATTEMPT -eq 0 ]; then
            echo -e "${BLUE}  → curl -s -H \"Authorization: Bearer $ADMIN_TOKEN\" \"$API_URL/admin/submissions/$SUBMISSION_ID\"${NC}" >&2
        fi
        SUBMISSION_RESPONSE=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" "$API_URL/admin/submissions/$SUBMISSION_ID")
        STATUS=$(json_value "$SUBMISSION_RESPONSE" "status")

        if [ "$STATUS" = "AWAITING_REVIEW" ]; then
            ML_COMPLETED=true
            break
        elif [ "$STATUS" = "ML_PROCESSING" ] || [ "$STATUS" = "PENDING" ]; then
            ATTEMPT=$((ATTEMPT + 1))
            if [ "$VERBOSE" = true ]; then
                echo "  ML processing... (attempt $ATTEMPT/$MAX_ATTEMPTS, status: $STATUS)"
            fi
            sleep 2
        else
            echo -e "${YELLOW}  Unexpected status: $STATUS${NC}"
            ATTEMPT=$((ATTEMPT + 1))
            sleep 2
        fi
    done

    if [ "$ML_COMPLETED" = true ]; then
        echo -e "${GREEN}  ✓ PASSED${NC}"
        pass_count=$((pass_count + 1))
        echo "  → ML processing completed (status: AWAITING_REVIEW)"
    else
        echo -e "${RED}  ✗ FAILED - ML processing did not complete in time${NC}"
        fail_count=$((fail_count + 1))
        echo "  Last status: $STATUS"
        echo ""
        echo "ML Service Logs:"
        docker-compose logs --tail=20 ml-service
    fi
    echo ""

    # ===================================
    # TEST 7: Admin Review and Approve
    # ===================================
    if [ "$ML_COMPLETED" = true ]; then
        run_test "Admin Review and Approve Submission"
        REVIEW_RESPONSE=$(api_request POST "/admin/submissions/$SUBMISSION_ID/review" "{\"approved\":true,\"rejectionReason\":null,\"adminNotes\":\"Docker test approval\"}" "$ADMIN_TOKEN" 200)
        if assert_success; then
            REVIEW_STATUS=$(json_value "$REVIEW_RESPONSE" "status")
            if [ "$REVIEW_STATUS" = "APPROVED" ]; then
                echo "  → Submission approved (status: APPROVED)"
            else
                echo -e "${YELLOW}  Unexpected status: $REVIEW_STATUS${NC}"
            fi
        fi
        echo ""

        # ===================================
        # TEST 8: Admin Assign Agent
        # ===================================
        if [ -n "$AGENT_ID" ]; then
            run_test "Admin Assign Agent to Submission"
            ASSIGN_RESPONSE=$(api_request POST "/admin/submissions/$SUBMISSION_ID/assign" "{\"agentId\":$AGENT_ID}" "$ADMIN_TOKEN" 200)
            if assert_success; then
                ASSIGN_STATUS=$(json_value "$ASSIGN_RESPONSE" "status")
                TOTAL_POINTS=$(json_value_number "$ASSIGN_RESPONSE" "totalPoints")
                if [ "$ASSIGN_STATUS" = "ASSIGNED" ]; then
                    echo "  → Agent assigned (status: ASSIGNED)"
                    echo "  → Total points awarded: $TOTAL_POINTS"
                else
                    echo -e "${YELLOW}  Unexpected status: $ASSIGN_STATUS${NC}"
                fi
            fi
            echo ""

            # ===================================
            # TEST 9: Agent Login
            # ===================================
            run_test "Agent Login"
            AGENT_LOGIN_RESPONSE=$(api_request POST "/auth/login/agent" "{\"username\":\"$AGENT_USERNAME\",\"password\":\"$AGENT_PASSWORD\"}" "" 200)
            if assert_success; then
                AGENT_TOKEN=$(json_value "$AGENT_LOGIN_RESPONSE" "accessToken")
                if [ -n "$AGENT_TOKEN" ]; then
                    echo "  → Agent token obtained"
                else
                    echo -e "${RED}  ✗ Failed to extract agent token${NC}"
                fi
            fi
            echo ""

            # ===================================
            # TEST 10: Agent Confirm Pickup
            # ===================================
            if [ -n "$AGENT_TOKEN" ]; then
                run_test "Agent Confirm Pickup"
                PICKUP_RESPONSE=$(api_request POST "/agents/submissions/$SUBMISSION_ID/pickup" "{\"notes\":\"Docker test pickup\"}" "$AGENT_TOKEN" 200)
                if assert_success; then
                    PICKUP_STATUS=$(json_value "$PICKUP_RESPONSE" "status")
                    if [ "$PICKUP_STATUS" = "PICKED_UP" ]; then
                        echo "  → Pickup confirmed (status: PICKED_UP)"
                    else
                        echo -e "${YELLOW}  Unexpected status: $PICKUP_STATUS${NC}"
                    fi
                fi
                echo ""
            fi

            # ===================================
            # TEST 11: Verify Client Points
            # ===================================
            run_test "Verify Client Points Updated"
            CLIENT_CHECK_RESPONSE=$(api_request GET "/admin/clients" "" "$ADMIN_TOKEN" 200)
            if assert_success; then
                CLIENT_TOTAL_POINTS=$(json_value_number "$CLIENT_CHECK_RESPONSE" "totalPoints")
                if [ "$CLIENT_TOTAL_POINTS" -gt 0 ]; then
                    echo "  → Client points updated: $CLIENT_TOTAL_POINTS"
                    if [ "$CLIENT_TOTAL_POINTS" -eq "$TOTAL_POINTS" ]; then
                        echo "  → Points match submission total ✓"
                    else
                        echo -e "${YELLOW}  Points mismatch: Client=$CLIENT_TOTAL_POINTS, Submission=$TOTAL_POINTS${NC}"
                    fi
                else
                    echo -e "${YELLOW}  Client has 0 points${NC}"
                fi
            fi
            echo ""
        fi
    fi
fi

# Stop services if requested
if [ "$AUTO_STOP" = true ] && [ "$SERVICES_WERE_RUNNING" = false ]; then
    echo ""
    echo "Stopping Docker Compose services..."
    docker-compose down
    echo -e "${GREEN}✓ Services stopped${NC}"
    echo ""
fi

# Print summary
echo ""
echo "======================================"
echo "Test Summary"
echo "======================================"
echo -e "Total tests:  $test_count"
echo -e "${GREEN}Passed:       $pass_count${NC}"
echo -e "${RED}Failed:       $fail_count${NC}"
echo "======================================"
echo ""

if [ $fail_count -eq 0 ]; then
    echo -e "${GREEN}✓ All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}✗ Some tests failed${NC}"
    echo ""
    echo "To view service logs:"
    echo "  docker-compose logs api"
    echo "  docker-compose logs ml-service"
    echo "  docker-compose logs rabbitmq"
    exit 1
fi
