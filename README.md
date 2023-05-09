# Integration Project - Group #26

Integration Project is a fully distributed multi-hop ad-hoc chat application using emulated wireless sound communication.

## Getting Started

Use the following command to install the required libraries for the project:

```bash
./gradlew build
```

## Usage for Intellij IDEA
To start using the chat, a user should run the "ChatApplication" class located at "/src/main/java/com/group26/"

It is necessary to wait before a user completely joins a server before being able to send messages. After that, they will be welcomed with an introduction screen:

```
[STATE] : FINDING_NEIGHBORS
[STATE] : ASSIGNING_ID
[STATE] : PULLING_TOPOLOGY
[STATE] : READY_TO_SEND

Welcome to chat 'Group 26'
Your username is Green
Type a message or '/help' to see additional commands
``` 

Then, a user can send messages by simply typing them into the terminal. Also, they can use the following commands to interact with the chat:
```
/help          - get possible commands
/users         - get a list of online users
/neighbors     - get neighbors of each user
/quit          - quit the server
```
## Recommendations
- Launch nodes 1 by 1 as there might be cases when nodes do not converge completely, or they assign the same id to themselves
- There might be concurrency issues if one of the nodes leaves the network (e.g. by using the /quit command)