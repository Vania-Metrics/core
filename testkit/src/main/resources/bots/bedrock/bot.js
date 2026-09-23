// Joins through Geyser as an offline Bedrock player, stays STAY_SECONDS, leaves.
// Prints BOT_SPAWNED once in the world: the harness waits for that line.
// Exit code 0 only if it spawned and left on its own.
const bedrock = require('bedrock-protocol')

const stay = Number(process.env.STAY_SECONDS || 20) * 1000
const client = bedrock.createClient({
  host: process.env.SERVER_HOST,
  port: Number(process.env.SERVER_PORT || 19132),
  username: process.env.BOT_NAME || 'probe',
  offline: true,
  // Native RakNet, compiled in the image: the pure JavaScript one cannot discover Geyser.
  raknetBackend: 'raknet-native',
  ...(process.env.BEDROCK_VERSION ? { version: process.env.BEDROCK_VERSION } : {}),
})

let spawned = false
let leaving = false

client.once('spawn', () => {
  spawned = true
  console.log('BOT_SPAWNED')
  setTimeout(() => {
    leaving = true
    console.log('BOT_QUITTING')
    client.disconnect('bye')
    setTimeout(() => process.exit(0), 1000)
  }, stay)
})

client.on('kick', (reason) => console.log('BOT_KICKED ' + JSON.stringify(reason)))
client.on('error', (err) => console.log('BOT_ERROR ' + (err && err.message)))
client.on('close', () => {
  console.log('BOT_ENDED')
  process.exit(spawned && leaving ? 0 : 1)
})

setTimeout(() => {
  console.log('BOT_TIMEOUT')
  process.exit(2)
}, stay + 120000)
