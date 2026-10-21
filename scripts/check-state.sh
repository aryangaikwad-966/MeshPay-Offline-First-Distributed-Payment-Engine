#!/bin/bash

# Quick state check script

PAYMENT_SERVICE_URL="http://localhost:8081"

echo "=========================================="
echo "MeshPay System State Check"
echo "=========================================="
echo ""
echo "📊 Payment Overview:"
echo "----------------------------------------"
curl -s "$PAYMENT_SERVICE_URL/api/payment/ops/overview" | python3 -m json.tool
echo ""
echo "🕐 Checked at: $(date '+%Y-%m-%d %H:%M:%S')"
echo ""
echo "💡 Create a payment in the UI to see changes!"
echo "   http://localhost:3001 → Offline Client tab"
echo ""
