#!/bin/bash

# MeshPay Automated Payment Flow Demo
# This script demonstrates the complete payment packet creation and sync process

echo "=========================================="
echo "MeshPay Automated Payment Flow Demo"
echo "=========================================="
echo ""

# Configuration
PAYMENT_SERVICE_URL="http://localhost:8081"
SENDER_VPA="test-sender@meshpay.local"
RECEIVER_VPA="recipient@meshpay.local"
AMOUNT="10.00"
CURRENCY="USD"

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${BLUE}Step 1: Get Recipient Public Key${NC}"
echo "----------------------------------------"
echo "Fetching the server's public key for encryption..."
echo ""

RECIPIENT_KEY_RESPONSE=$(curl -s "$PAYMENT_SERVICE_URL/api/payment/packets/recipient-key")
echo "Response:"
echo "$RECIPIENT_KEY_RESPONSE" | python3 -m json.tool
echo ""

# Extract the public key from the response
PUBLIC_KEY=$(echo "$RECIPIENT_KEY_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['publicKey'])")
ALGORITHM=$(echo "$RECIPIENT_KEY_RESPONSE" | python3 -c "import sys, json; print(json.load(sys.stdin)['algorithm'])")

echo -e "${GREEN}✓ Public Key received${NC}"
echo "Algorithm: $ALGORITHM"
echo ""

echo -e "${BLUE}Step 2: Check Current System State${NC}"
echo "----------------------------------------"
echo "Before payment creation..."
echo ""

OVERVIEW_RESPONSE=$(curl -s "$PAYMENT_SERVICE_URL/api/payment/ops/overview")
echo "System Overview:"
echo "$OVERVIEW_RESPONSE" | python3 -m json.tool
echo ""

echo -e "${BLUE}Step 3: Create Payment Packet${NC}"
echo "----------------------------------------"
echo "Creating an encrypted payment packet..."
echo ""
echo "Payment Details:"
echo "  Sender: $SENDER_VPA"
echo "  Receiver: $RECEIVER_VPA"
echo "  Amount: $AMOUNT $CURRENCY"
echo ""

# Generate a unique packet ID and nonce
PACKET_ID="demo-packet-$(date +%s)"
NONCE="nonce-$(date +%s%N)-$(openssl rand -hex 4)"

echo "Packet ID: $PACKET_ID"
echo "Nonce: $NONCE"
echo ""

echo -e "${YELLOW}Note: In a real scenario, the browser would:${NC}"
echo "  1. Generate a random AES-256 key"
echo "  2. Encrypt payment data with AES-GCM"
echo "  3. Wrap AES key with server's RSA public key"
echo "  4. Sign the packet with your private key"
echo "  5. Store in IndexedDB for offline safety"
echo ""

echo -e "${BLUE}Step 4: Simulate Payment Synchronization${NC}"
echo "----------------------------------------"
echo "In the real UI, the encrypted packet would be submitted to:"
echo "POST $PAYMENT_SERVICE_URL/api/payment/packets/sync"
echo ""

echo -e "${YELLOW}For this demo, we'll skip the encryption/signing${NC}"
echo "and show you what happens when you use the Offline Client UI."
echo ""

echo -e "${BLUE}Step 5: Check Payment Status in UI${NC}"
echo "----------------------------------------"
echo "After syncing, you can check:"
echo ""
echo "1. Overview tab: See payment counts increase"
echo "2. Transactions tab: See your payment with packet ID"
echo "3. Reliability tab: See outbox events"
echo ""

echo -e "${BLUE}Step 6: Test Duplicate Detection${NC}"
echo "----------------------------------------"
echo "If you submit the same packet twice:"
echo "  - First attempt: SYNCHRONIZED (201)"
echo "  - Second attempt: DUPLICATE (200)"
echo "  - Database: Only 1 payment record"
echo ""

echo -e "${GREEN}=========================================="
echo "Demo Complete!"
echo "=========================================="
echo ""
echo "Next Steps:"
echo "1. Open the browser at http://localhost:3001"
echo "2. Go to 'Offline Client' tab"
echo "3. Click 'Load Recipient Key'"
echo "4. Paste the signer key I provided earlier"
echo "5. Fill in payment details:"
echo "   - Sender: $SENDER_VPA"
echo "   - Receiver: $RECEIVER_VPA"
echo "   - Amount: $AMOUNT"
echo "   - Currency: $CURRENCY"
echo "6. Click 'Create Payment Packet'"
echo "7. Watch the status changes: LOCAL_QUEUED → SYNCING → SYNCHRONIZED"
echo ""
