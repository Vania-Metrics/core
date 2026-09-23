// Joins a server, jumps now and then, leaves after STAY_SECONDS.
// Prints BOT_SPAWNED once in the world: the harness waits for that line.
// Exit code 0 only if it spawned and left on its own.
const mineflayer = require('mineflayer')

const stay = Number(process.env.STAY_SECONDS || 20) * 1000
const bot = mineflayer.createBot({
  host: process.env.SERVER_HOST,
  port: Number(process.env.SERVER_PORT || 25565),
  username: process.env.BOT_NAME || 'probe',
  version: process.env.MC_VERSION || false,
  auth: 'offline',
})

let spawned = false
let leaving = false

bot.once('spawn', () => {
  spawned = true
  console.log('BOT_SPAWNED')
  const jump = setInterval(() => {
    bot.setControlState('jump', true)
    setTimeout(() => bot.setControlState('jump', false), 300)
  }, 2000)
  setTimeout(() => {
    clearInterval(jump)
    leaving = true
    console.log('BOT_QUITTING')
    bot.quit()
  }, stay)
})

bot.on('kicked', (reason) => console.log('BOT_KICKED ' + JSON.stringify(reason)))
bot.on('error', (err) => console.log('BOT_ERROR ' + err.message))
bot.on('end', (reason) => {
  console.log('BOT_ENDED ' + reason)
  process.exit(spawned && leaving ? 0 : 1)
})

setTimeout(() => {
  console.log('BOT_TIMEOUT')
  process.exit(2)
}, stay + 120000)
