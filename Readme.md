# Rumble

## Abstract 

Rumble is a decentralized and delay-tolerant twitter-like mobile application. 
It enables the spread of messages in an epidemic fashion using automatically formed and opportunistic local ad-hoc network. Every message sent or received with are stored on the local database and pushed to every other device it meets. By doing so, messages naturally propagates throughout the network using social links as the underlying infrastructure. Because it doesn't rely on any fixed infrastructure like the Internet, it is naturally resistant against censorship. 

Because a large number of message may exists, Rumble prioritized messages based on various parameters such as hashtag subscriptions, replication density, date of creation etc.

By default, everyone belongs to the "rumble.public" group which is open but Rumble also enable the possibility to create private communities encrypted with AES-128 (CBC/PKCS5). A message that belongs to a private community only propagates through members of this community, and a new member can only join a community after being invited or "vouched" by another member. 

Rumble also support a real-time "chat" to exchange message with the current neighborhood only. In this mode, messages are not forwarded any further. 


## Technical Details

Whenever Rumble is running, it automatically discover its neighborhood (bluetooth and wifi) and forms local area network. Each time a new communication channel is created between two devices, they exchange their "preferences" and start exchanging the messages with each other. 
