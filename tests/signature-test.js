const fs = require('fs');
const assert = require('assert');
const { webcrypto } = require('crypto');
global.crypto = webcrypto;
const path = process.argv[2] || 'src/editor.html';
const html = fs.readFileSync(path, 'utf8');
const source = html.slice(html.indexOf('const signatureKey='), html.indexOf('\nasync function updatePreview()'));

(async () => {
  const keys = await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'}, true, ['sign','verify']);
  const publicKey = Buffer.from(await crypto.subtle.exportKey('spki', keys.publicKey)).toString('base64');
  const testSource = source.replace(/const signatureKey='[^']+'/,'const signatureKey='+JSON.stringify(publicKey));
  const verify = new Function(testSource+'; return verifySignature;')();
  const uid = '01020304050607';
  const prefix = 'BC0FCB9701020304';
  const message = Buffer.from(uid+prefix,'hex');
  const signature = Buffer.from(await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'},keys.privateKey,message)).toString('hex');
  const raw = prefix+'00'.repeat(72)+signature;
  assert.strictEqual(await verify(raw,uid),'Valid signature');
  assert.strictEqual(await verify(raw,'00020304050607'),'Invalid signature');
  assert.strictEqual(await verify('BC0FCB9701020305'+raw.slice(16),uid),'Invalid signature');
  assert.strictEqual(await verify(raw,''),'Cannot verify: tag UID unavailable');
  assert.strictEqual(await verify(raw.slice(0,160)+'00'.repeat(64),uid),'Unsigned');
  console.log('Signature verification tests passed.');
})().catch(e => { console.error(e); process.exitCode = 1; });
