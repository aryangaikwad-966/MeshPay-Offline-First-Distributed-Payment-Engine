#!/usr/bin/env node

/**
 * MeshPay Payment Packet Creation Demo
 * This script demonstrates how to create an encrypted and signed payment packet
 * Similar to what the browser UI does with Web Crypto API
 */

const crypto = require('crypto');

// Configuration
const RECIPIENT_PUBLIC_KEY_B64 = 'MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAqGQ4412cW/5hwQrkPlRcYd3hLEwy7590F2R3/QaRWQUtblPqszcNRpxT/w4Vd1DQZuv7pqiDAxWWon0p71vqxFSI37JbEIHYTjdBUNWZIdNGZGJdlzPTycYUXxf41UOC3QukSQQRp2B/R6aW0y+nNKAri3mOMXIwnup22BeKk8OZUzAloE/1N3BsZoonfzgScB3IqfA+ro1sj5c43jo5kRBVhhllFzUaUx+jz5SQYRHTujD/2MYoaYBtoq8tu514AULWG89QPT6i4yvb6Gv+XSaIGFEJeUeZlk6lUeUY2BaqFzgCUt0YWF4JpYVZiyO7XmQyq1TLYelgE1xglXkqacKFjdIovbEFyk4X8GSU2wkhfeWeFD72PpNPRdxBfyUUJLm2Rt2LxXId6WGBAfDWVmXq+9+h7+6NuJY5vQ4nuSnwNG4vunrjpDwlagPo8RcJrMsqyucyvIFvnKzl0xKAEmILjUI0c2Wcf9n/uuBAEBUdswIetZKLyFk3dpLh4qq/AgMBAAE=';

const SIGNER_PRIVATE_KEY_B64 = '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';

// Payment details
const payment = {
  packetId: `demo-packet-${Date.now()}`,
  senderVpa: 'test-sender@meshpay.local',
  receiverVpa: 'recipient@meshpay.local',
  amount: '10.00',
  currency: 'USD',
  signedAt: Date.now(),
  nonce: `nonce-${Date.now()}-${crypto.randomBytes(4).toString('hex')}`
};

console.log('==========================================');
console.log('MeshPay Payment Packet Creation Demo');
console.log('==========================================\n');

console.log('Step 1: Payment Details');
console.log('----------------------------------------');
console.log(JSON.stringify(payment, null, 2));
console.log('');

console.log('Step 2: Generate Random AES-256 Key and IV');
console.log('----------------------------------------');
const aesKey = crypto.randomBytes(32);
const iv = crypto.randomBytes(12);
console.log(`AES Key length: ${aesKey.length} bytes`);
console.log(`IV length: ${iv.length} bytes`);
console.log('');

console.log('Step 3: Encrypt Payment Data with AES-GCM');
console.log('----------------------------------------');
const paymentJson = JSON.stringify(payment);
const aad = `meshpay-payment-aad-v1\n${payment.packetId}\n${payment.senderVpa}\n${payment.nonce}`;
const cipher = crypto.createCipheriv('aes-256-gcm', aesKey, iv);
cipher.setAAD(Buffer.from(aad));
let ciphertext = cipher.update(paymentJson, 'utf8');
cipher.final();
const authTag = cipher.getAuthTag();
const encryptedData = Buffer.concat([ciphertext, authTag]);
console.log(`Ciphertext length: ${encryptedData.length} bytes`);
console.log('');

console.log('Step 4: Wrap AES Key with RSA-OAEP');
console.log('----------------------------------------');
const recipientPublicKey = crypto.createPublicKey({
  key: Buffer.from(RECIPIENT_PUBLIC_KEY_B64, 'base64'),
  format: 'der',
  type: 'spki'
});
const wrappedKey = crypto.publicEncrypt({
  key: recipientPublicKey,
  padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
  oaepHash: 'sha256'
}, aesKey);
console.log(`Wrapped key length: ${wrappedKey.length} bytes`);
console.log('');

console.log('Step 5: Encode to Base64');
console.log('----------------------------------------');
const iv64 = iv.toString('base64');
const wrapped64 = wrappedKey.toString('base64');
const ciphertext64 = encryptedData.toString('base64');
console.log('IV (base64):', iv64.substring(0, 50) + '...');
console.log('Wrapped Key (base64):', wrapped64.substring(0, 50) + '...');
console.log('Ciphertext (base64):', ciphertext64.substring(0, 50) + '...');
console.log('');

console.log('Step 6: Create Canonical String for Signature');
console.log('----------------------------------------');
const canonical = `meshpay-encrypted-packet-v1\n${payment.packetId}\n${payment.senderVpa}\n${payment.nonce}\n${iv64}\n${wrapped64}\n${ciphertext64}`;
console.log('Canonical string length:', canonical.length);
console.log('');

console.log('Step 7: Sign with RSA-PSS');
console.log('----------------------------------------');
const signerPrivateKey = crypto.createPrivateKey({
  key: Buffer.from(SIGNER_PRIVATE_KEY_B64, 'base64'),
  format: 'der',
  type: 'pkcs8'
});
const signature = crypto.sign('sha256', Buffer.from(canonical), {
  key: signerPrivateKey,
  padding: crypto.constants.RSA_PKCS1_PSS_PADDING,
  saltLength: crypto.constants.RSA_PSS_SALTLEN_DIGEST
});
const signature64 = signature.toString('base64');
console.log(`Signature length: ${signature.length} bytes`);
console.log('');

console.log('Step 8: Create Payment Packet Request');
console.log('----------------------------------------');
const packetRequest = {
  packetId: payment.packetId,
  senderVpa: payment.senderVpa,
  senderNonce: payment.nonce,
  iv: iv64,
  wrappedAesKey: wrapped64,
  ciphertext: ciphertext64,
  signature: signature64,
  bridgeNodeId: 'demo-bridge-1',
  hopCount: 0
};
console.log(JSON.stringify(packetRequest, null, 2));
console.log('');

console.log('==========================================');
console.log('Packet Created Successfully!');
console.log('==========================================\n');

console.log('Next Steps:');
console.log('1. Submit this packet to: POST http://localhost:8081/api/payment/packets/sync');
console.log('2. The server will validate, decrypt, and process the payment');
console.log('3. Check the UI to see the payment status');
console.log('');

// Save to file for reference
const fs = require('fs');
fs.writeFileSync('/tmp/meshpay-packet.json', JSON.stringify(packetRequest, null, 2));
console.log('Packet saved to: /tmp/meshpay-packet.json');
console.log('');
