# TicTacFive - S3 Description

Basic Idea:
  - use two buckets to store info:
  Bucket #1:  playerinfo.mnetz - stores files & data related to a player info object that's been
  serialized and stored in the S3 bucket (player turn, message, etc.)
  Bucket #2: board.mnetz - stores files & data related to board state (row, last move played)

Logic:
  - when a player completes their turn, leave a serialized playerinfo file w/ a key matching the other
  player's player id
  - it's your turn when a file w/ the key matching your player id is in the playerinfo.mnetz bucket
  - when it's your turn, download the file w/ your player id then delete it from the bucket to avoid confusion
  - use the checkTurn method to periodically check if a file w/ key matching your player id exists
  
Notes:
  - on start up, to determine who goes first there are 2 basic options at this point:
  1. check for file w/ key "playertwo", if it exists, player one must have put it there & is therefore connected
  2. add secondary files w/ keys "playeroneconnected" & "playertwoconnected" to determine who is connected, add
  your corresponding file on start up, delete on exit
    - there's more logic needed for this version, but might be work the extra work
  - IMPORTANT: this approach requires the buckets to be emptied before reuse to avoid confusion
  (on exit, delete playerinfo and board files from buckets)
