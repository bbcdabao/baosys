// Generates persistent JWK material once. Never overwrite an existing signing key.
const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')
const output = process.argv[2]
if (!output) throw new Error('Usage: node scripts/generate-signing-key.cjs <output.json>')
const { privateKey } = crypto.generateKeyPairSync('rsa', { modulusLength: 3072 })
const key = privateKey.export({ format: 'jwk' })
key.kid = crypto.createHash('sha256')
  .update(JSON.stringify({ e: key.e, kty: key.kty, n: key.n })).digest('base64url')
key.use = 'sig'
key.alg = 'RS256'
fs.mkdirSync(path.dirname(path.resolve(output)), { recursive: true })
fs.writeFileSync(output, JSON.stringify({ keys: [key] }, null, 2), { flag: 'wx', mode: 0o600 })
console.log(`Created ${path.resolve(output)}; restrict access to the authorization server account.`)
