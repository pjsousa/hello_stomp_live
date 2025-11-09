
App overview:

We will build a chat-like real time web-app.
The users will be able to choose to send messages to everyone online or to a specific user. 
The system will  broadcast periodic messages to everyone. 
The system will send periodic customizable messages to each user. 


R1: The UI has a messages pane, showing the messages received

R2: The information presented for each mesaage should be: timestamp(hour:minute:second), sender name, message

R3: there should be a"ME" field for the users to identify themselves. The "ME" field is a dropdown with 10 predefined animal emojis

R4: There is no other user identifier than the emoji they selected

R5: there should be a "SEND TO" for the users to chose who to send their messages to. The "SEND TO" is a button. With "EVERYONE” by default.

R6: when the "SEND TO" button is clicked, its label cycles through the possible online users and also "EVERYONE"

R7: the online users list needs to update in realtime, to that we can SEND TO them as soon as they become obline. 

R8: There is a "SEND ME" field. The send me field is a dropdown with food emojis. The SEND ME value that is set in SEND ME field  is what the system will send to all devices of this specific user on a scheduled basis

R8: There is a "SEND HERE" field. The send HERE field is a dropdown with food emojis. The SEND HERE value that is set in SEND HERE  field  is what the system will send to this specific device on a scheduled basis

R9: There is a SEND US fiield. The SEND US field is a dropdown also with the same set of possible  food emojis. SEND US value is what the systen will broadcast to everyone on a different schedule

R10: Anyone can change the system broadcast SEND US value. When they do, the system will send that on the next schedule and UIs on that field will update for everyone. So the SEND US field are synchronized across everyone’s devices

R10b: the user can change the SEND ME value on any of its devices. When they do, the system will send that on the next schedule and UIs on that field will update for all devices of the user. So the SEND ME fields are synchronized across all devices of the same user

R11: there should be a MESSAGE button and a SEND button

R12: when the user presses the MESSAGE button , its label cycles through the same set of food emojis 

R13: when the user clicks the SEND button, whatever label is on "MESSAGE" gets sent to the "SEND TO" label

R14: if SENT TO  is set to "Everyone", the message is broadcasted to all users

R15: when the UI first loads, it should display the last 10 messages 

R16: the implementation should be a Java spring api with Stomp endpoints. All communication is through stomp sockets. The webapp is a static page using js modules served statically from the resources. 

R17: the server should have 3 schedules: broadcast, user and device. To send the SEND US, SEND ME and SEND HERE current values that the users specified

R18: the server should have as many topic, user and queue endpoints as needed to cover all communications for message sending, UI configuration and user and message 
customization as specified in all other requirements