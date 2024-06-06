# DeathCommandsCooldowns

A Spigot plugin to add a cooldown on commands after death.
Original request: www.spigotmc.org/threads/essentials-back-plugin.649840/

## Configuration

```events:
  # should the cooldown be activated on death?
  on-death: true
  # should the cooldown be activated on pvp death?
  # warning: if on-death is true, this will be ignored
  on-pvp-death: true

cooldowns:
  # the default cooldown time in seconds
  default: 10
  # the cooldown time in seconds with it's associated permission
  # replace dots with underscores in the permission node due to yaml limitations
  pvp_timer_first: 8
  pvp_timer_second: 5
  pvp_timer_third: 2

messages:
    # the message to be sent when the player tries to use /back while on cooldown
    cooldown: '&cYou cannot use this command for another &4{time} &cseconds!'

# list of commands to blacklist
blacklist-commands:
  - back
  - eback
  - return
  - ereturn
  - essentials:back
  - essentials:eback
  - essentials:return
  - essentials:ereturn```
