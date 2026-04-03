#!/bin/bash

#=============================================================================
# Brief     : Interoperability Test against tftp-hpa
# Author(s) : Carlos Guimarães <carlos.em.guimaraes@gmail.com>
# ----------------------------------------------------------------------------
# ssTFTP - Open Trivial File Transfer Protocol
#
# Copyright (C) 2008-2023 Carlos Guimarães
#
# This file is part of ssTFTP.
#
# ssTFTP is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# ssTFTP is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with ssTFTP. If not, write to the Free Software Foundation,
# Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
#=============================================================================

set -e

# Get repository root directory (works in both local and CI/CD environments)
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

TFTP_PORT=6969
SSTFTP_SERVER_PORT=6970
TFTP_HPA_ROOT="/tmp/tftp-hpa-root"
SSTFTP_SERVER_ROOT="/tmp/sstftp-server-root"
LOG_FILE="/tmp/tftp-hpa-test.log"
SSTFTP_CLIENT_JAR="$REPO_ROOT/sstftp-client/target/sstftp-client-0.2.jar"
SSTFTP_SERVER_JAR="$REPO_ROOT/sstftp-server/target/sstftp-server-0.2.jar"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

cleanup() {
    echo "[INFO] Cleaning up resources..."
    # Kill any running ssTFTP server instances
    pkill -f "sstftp-server" || true
    # Stop tftp-hpa container if running
    docker stop tftp-hpa-test 2>/dev/null || true
    docker rm tftp-hpa-test 2>/dev/null || true
    # Clean up directories
    rm -rf "$TFTP_HPA_ROOT" "$SSTFTP_SERVER_ROOT"
}

trap cleanup EXIT

log_test() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1" | tee -a "$LOG_FILE"
}

pass_test() {
    echo -e "${GREEN}[PASS]${NC} $1" | tee -a "$LOG_FILE"
}

fail_test() {
    echo -e "${RED}[FAIL]${NC} $1" | tee -a "$LOG_FILE"
    return 1
}

log_test "========================================="
log_test "TFTP-HPA Interoperability Tests"
log_test "========================================="

# Setup directories
mkdir -p "$TFTP_HPA_ROOT" "$SSTFTP_SERVER_ROOT"
chmod 777 "$TFTP_HPA_ROOT" "$SSTFTP_SERVER_ROOT"

# Create test files
log_test "Creating test files..."
dd if=/dev/zero of="$TFTP_HPA_ROOT/test_512.bin" bs=512 count=1 2>/dev/null
dd if=/dev/zero of="$TFTP_HPA_ROOT/test_1024.bin" bs=1024 count=1 2>/dev/null
dd if=/dev/zero of="$TFTP_HPA_ROOT/test_text.txt" bs=1 count=1000 </dev/urandom 2>/dev/null
echo "Hello TFTP World" > "$TFTP_HPA_ROOT/test_simple.txt"

# Start tftp-hpa server in Docker
log_test "Starting tftp-hpa server..."
docker run -d \
    --name tftp-hpa-test \
    --net host \
    -v "$TFTP_HPA_ROOT:/tftp:Z" \
    ubuntu:22.04 \
    bash -c "set -x; apt-get update -qq && apt-get install -y tftpd-hpa && mkdir -p /tftp && chmod 777 /tftp && exec in.tftpd -v -l -s /tftp" \
    > /dev/null 2>&1

# Wait for tftp-hpa to be ready
log_test "Waiting for tftp-hpa server to start..."
for i in {1..15}; do
    if docker ps | grep -q tftp-hpa-test; then
        sleep 1
    else
        log_test "ERROR: tftp-hpa container exited"
        docker logs tftp-hpa-test 2>&1 | tee -a "$LOG_FILE"
        break
    fi
done
sleep 2

# Test 1: ssTFTP Client GET from tftp-hpa Server (octet mode)
log_test "---"
log_test "Test 1: ssTFTP Client GET from tftp-hpa (octet mode)"
mkdir -p /tmp/sstftp-client-get
cd /tmp/sstftp-client-get

if java -jar "$SSTFTP_CLIENT_JAR" -a get -f test_512.bin -c localhost -p $TFTP_PORT -m octet 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f test_512.bin ]; then
        if cmp -s "$TFTP_HPA_ROOT/test_512.bin" test_512.bin; then
            pass_test "GET test_512.bin with octet mode"
        else
            fail_test "GET test_512.bin: file content mismatch"
        fi
    else
        fail_test "GET test_512.bin: file not downloaded"
    fi
else
    fail_test "GET test_512.bin: download failed"
fi

cd - > /dev/null

# Test 2: ssTFTP Client GET from tftp-hpa Server (netascii mode)
log_test "---"
log_test "Test 2: ssTFTP Client GET from tftp-hpa (netascii mode)"
mkdir -p /tmp/sstftp-client-get-ascii
cd /tmp/sstftp-client-get-ascii

if java -jar "$SSTFTP_CLIENT_JAR" -a get -f test_simple.txt -c localhost -p $TFTP_PORT -m netascii 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f test_simple.txt ]; then
        pass_test "GET test_simple.txt with netascii mode"
    else
        fail_test "GET test_simple.txt: file not downloaded"
    fi
else
    fail_test "GET test_simple.txt: download failed"
fi

cd - > /dev/null

# Test 3: ssTFTP Client PUT to tftp-hpa Server (octet mode)
log_test "---"
log_test "Test 3: ssTFTP Client PUT to tftp-hpa (octet mode)"
mkdir -p /tmp/sstftp-client-put
cd /tmp/sstftp-client-put
dd if=/dev/zero of=test_put_512.bin bs=512 count=1 2>/dev/null
chmod 644 test_put_512.bin

if java -jar "$SSTFTP_CLIENT_JAR" -a put -f test_put_512.bin -c localhost -p $TFTP_PORT -m octet 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f "$TFTP_HPA_ROOT/test_put_512.bin" ]; then
        if cmp -s test_put_512.bin "$TFTP_HPA_ROOT/test_put_512.bin"; then
            pass_test "PUT test_put_512.bin with octet mode"
        else
            fail_test "PUT test_put_512.bin: file content mismatch"
        fi
    else
        fail_test "PUT test_put_512.bin: file not uploaded to server"
    fi
else
    fail_test "PUT test_put_512.bin: upload failed"
fi

cd - > /dev/null

# Test 4: ssTFTP Client GET with blocksize option
log_test "---"
log_test "Test 4: ssTFTP Client GET with blocksize option (256)"
mkdir -p /tmp/sstftp-client-blksize
cd /tmp/sstftp-client-blksize

if java -jar "$SSTFTP_CLIENT_JAR" -a get -f test_1024.bin -c localhost -p $TFTP_PORT -b 256 2>&1 | tee -a "$LOG_FILE"; then
    if [ -f test_1024.bin ]; then
        if cmp -s "$TFTP_HPA_ROOT/test_1024.bin" test_1024.bin; then
            pass_test "GET test_1024.bin with blocksize 256"
        else
            fail_test "GET test_1024.bin: file content mismatch with blocksize 256"
        fi
    else
        fail_test "GET test_1024.bin: file not downloaded with blocksize 256"
    fi
else
    fail_test "GET test_1024.bin: download with blocksize 256 failed"
fi

cd - > /dev/null

# Test 5: Reference tftp-hpa Client GET from ssTFTP Server
log_test "---"
log_test "Test 5: Reference tftp-hpa Client GET from ssTFTP Server"

# Start ssTFTP server
log_test "Starting ssTFTP server..."
mkdir -p "$SSTFTP_SERVER_ROOT"
cp "$TFTP_HPA_ROOT/test_512.bin" "$SSTFTP_SERVER_ROOT/"
cp "$TFTP_HPA_ROOT/test_simple.txt" "$SSTFTP_SERVER_ROOT/"

cd $REPO_ROOT
java -jar "$SSTFTP_SERVER_JAR" -p $SSTFTP_SERVER_PORT -r "$SSTFTP_SERVER_ROOT" > /tmp/sstftp-server.log 2>&1 &
SSTFTP_PID=$!
sleep 3

mkdir -p /tmp/tftp-hpa-client-get
cd /tmp/tftp-hpa-client-get

if tftp -m octet -v localhost $SSTFTP_SERVER_PORT << 'EOF' 2>&1 | tee -a "$LOG_FILE"
get test_512.bin
quit
EOF
then
    if [ -f test_512.bin ]; then
        if cmp -s "$SSTFTP_SERVER_ROOT/test_512.bin" test_512.bin; then
            pass_test "Reference Client GET test_512.bin from ssTFTP Server"
        else
            fail_test "Reference Client GET test_512.bin: file content mismatch"
        fi
    else
        fail_test "Reference Client GET test_512.bin: file not downloaded"
    fi
else
    fail_test "Reference Client GET test_512.bin: download failed"
fi

cd - > /dev/null
kill $SSTFTP_PID 2>/dev/null || true
wait $SSTFTP_PID 2>/dev/null || true

# Test 6: Reference tftp-hpa Client PUT to ssTFTP Server
log_test "---"
log_test "Test 6: Reference tftp-hpa Client PUT to ssTFTP Server"

# Start ssTFTP server again
log_test "Starting ssTFTP server..."
cd $REPO_ROOT
java -jar "$SSTFTP_SERVER_JAR" -p $SSTFTP_SERVER_PORT -r "$SSTFTP_SERVER_ROOT" > /tmp/sstftp-server.log 2>&1 &
SSTFTP_PID=$!
sleep 3

mkdir -p /tmp/tftp-hpa-client-put
cd /tmp/tftp-hpa-client-put
dd if=/dev/zero of=test_put_ref.bin bs=512 count=1 2>/dev/null

if tftp -m octet -v localhost $SSTFTP_SERVER_PORT << 'EOF' 2>&1 | tee -a "$LOG_FILE"
put test_put_ref.bin
quit
EOF
then
    if [ -f "$SSTFTP_SERVER_ROOT/test_put_ref.bin" ]; then
        if cmp -s test_put_ref.bin "$SSTFTP_SERVER_ROOT/test_put_ref.bin"; then
            pass_test "Reference Client PUT test_put_ref.bin to ssTFTP Server"
        else
            fail_test "Reference Client PUT test_put_ref.bin: file content mismatch"
        fi
    else
        fail_test "Reference Client PUT test_put_ref.bin: file not uploaded to server"
    fi
else
    fail_test "Reference Client PUT test_put_ref.bin: upload failed"
fi

cd - > /dev/null
kill $SSTFTP_PID 2>/dev/null || true
wait $SSTFTP_PID 2>/dev/null || true

log_test "---"
log_test "========================================="
log_test "tftp-hpa Interoperability Tests Completed"
log_test "========================================="

exit 0
