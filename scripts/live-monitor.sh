#!/bin/bash

# MeshPay Live Payment Monitor
# Watch the system state in real-time as you create payments

PAYMENT_SERVICE_URL="http://localhost:8081"

echo "=========================================="
echo "MeshPay Live Payment Monitor"
echo "=========================================="
echo ""
echo "Monitoring system state..."
echo "Press Ctrl+C to stop"
echo ""
echo "💡 Try creating a payment in the UI at http://localhost:3001"
echo "   Go to Offline Client tab → Load Recipient Key → Paste signer key → Create payment"
echo ""
echo "=========================================="
echo ""

while true; do
    clear
    echo "=========================================="
    echo "MeshPay Live Payment Monitor"
    echo "=========================================="
    echo ""
    echo "📊 Current System State"
    echo "----------------------------------------"
    curl -s "$PAYMENT_SERVICE_URL/api/payment/ops/overview" | python3 -m json.tool
    echo ""
    echo "🕐 Last Updated: $(date '+%Y-%m-%d %H:%M:%S')"
    echo ""
    echo "💡 Create a payment in the UI to see changes!"
    echo "   http://localhost:3001 → Offline Client tab"
    echo ""
    echo "Press Ctrl+C to stop monitoring"
    echo "=========================================="
    sleep 3
done
