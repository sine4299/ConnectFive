
// Fig. 24.15: TicTacToeClient.java
// Client that let a user play Tic-Tac-Toe with another across a network.
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.net.Socket;
import java.net.InetAddress;
import java.io.IOException;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
//import aws
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.AttributeUpdate;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.UpdateItemOutcome;
import com.amazonaws.services.dynamodbv2.document.spec.GetItemSpec;
import com.amazonaws.services.dynamodbv2.document.spec.UpdateItemSpec;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.UpdateItemRequest;
import com.amazonaws.services.dynamodbv2.model.UpdateItemResult;

import java.util.Formatter;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class TicTacToeClient extends JFrame implements Runnable 
{
   private JTextField idField; // textfield to display player's mark
   private JTextArea displayArea; // JTextArea to display output
   private JPanel boardPanel; // panel for tic-tac-toe board
   private JPanel panel2; // panel to hold board
   private Square board[][]; // tic-tac-toe board
   private Square currentSquare; // current square
   private String myMark; // this client's mark
   private boolean myTurn; // determines which client's turn it is
   private final String X_MARK = "X"; // mark for first client
   private final String O_MARK = "O"; // mark for second client
   private boolean waitingForOpponent = false;
   private final int bsize = 16;
   private DynamoDB dynamoDB;
   private static Table playerInfo;
   private Table gameInfo;
   private int playerId;

   // set up user-interface and board
   public TicTacToeClient( String host )
   { 
      displayArea = new JTextArea( 4, 30 ); // set up JTextArea
      displayArea.setEditable( false );
      add( new JScrollPane( displayArea ), BorderLayout.SOUTH );

      boardPanel = new JPanel(); // set up panel for squares in board
      boardPanel.setLayout( new GridLayout( bsize, bsize, 0, 0 ) ); //was 3

      board = new Square[ bsize ][ bsize ]; // create board

      // loop over the rows in the board
      for ( int row = 0; row < board.length; row++ ) 
      {
         // loop over the columns in the board
         for ( int column = 0; column < board[ row ].length; column++ ) 
         {
            // create square. initially the symbol on each square is a white space.
            board[ row ][ column ] = new Square( " ", row * bsize + column );
            boardPanel.add( board[ row ][ column ] ); // add square       
         } // end inner for
      } // end outer for

      idField = new JTextField(); // set up textfield
      idField.setEditable( false );
      add( idField, BorderLayout.NORTH );
      
      panel2 = new JPanel(); // set up panel to contain boardPanel
      panel2.add( boardPanel, BorderLayout.CENTER ); // add board panel
      add( panel2, BorderLayout.CENTER ); // add container panel

      setSize( 600, 600 ); // set size of window
      setVisible( true ); // show window

      startClient();
   } // end TicTacToeClient constructor

   // start the client thread
   public void startClient()
   {  
	   setPlayerInfo();
      // create and start worker thread for this client
      ExecutorService worker = Executors.newFixedThreadPool( 1 );
      worker.execute( this ); // execute client
   } // end method startClient
   
   public void setPlayerInfo(){
		 //Setup a connection to DynamoDB
		   AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard();
		   AmazonDynamoDB ddb = AmazonDynamoDBClientBuilder.standard()
	               .withRegion(Regions.US_WEST_2)
	               .withCredentials(new ProfileCredentialsProvider("default"))
	               .build();
		   dynamoDB = new DynamoDB(ddb);
	       playerInfo = dynamoDB.getTable("PlayerInfo");
	       gameInfo = dynamoDB.getTable("Board");

	       GetItemSpec spec = new GetItemSpec()
	                .withPrimaryKey("PlayerId", 1); 

	       try {
	           //System.out.println("Attempting to read the item...");
	           Item outcome = playerInfo.getItem(spec);
	           boolean conn = outcome.getBOOL("IsConnected");
	           UpdateItemSpec updateItemSpec;
	           UpdateItemOutcome itemOutcome;
	           //Sets the player mark X for player 1  and O for player 2
	           if(!conn){
	        	   myMark =  X_MARK;
	        	   //The the player is the first to connect then they get first turn
	        	   waitingForOpponent = true;
	        	   //Updates the DB to say that is player is connected as player 1
	        	   playerId = 1;
	        	   updateItemSpec = new UpdateItemSpec()
	        	                    	.withPrimaryKey("PlayerId", playerId)
	        	                    	.addAttributeUpdate(new AttributeUpdate("IsConnected").put(true));
	        	   itemOutcome = playerInfo.updateItem(updateItemSpec);
	        	   displayMessage("Player X connected\n");
	        	   displayMessage("Waiting for other player to connect\n");
	           }
	           else{
	        	   myMark = O_MARK;
	        	   //Updates the DB to say that is player is connected as player 2
	        	   playerId = 2;
	        	   updateItemSpec = new UpdateItemSpec()
	                   	.withPrimaryKey("PlayerId", playerId)
	                   	.addAttributeUpdate(new AttributeUpdate("IsConnected").put(true));
	        	   itemOutcome = playerInfo.updateItem(updateItemSpec);
	        	   myTurn = false;
	           }

	       } catch (Exception e) {
	           System.err.println("Unable to read item: " + " in setPlayerInfo");
	           System.err.println(e.getMessage());
	       }
	   }
   // control thread that allows continuous update of displayArea
   public void run()
   {

      SwingUtilities.invokeLater( 
         new Runnable() 
         {         
            public void run()
            {
               // display player's mark
               idField.setText( "You are player \"" + myMark + "\"" );
            } // end method run
         } // end anonymous inner class
      ); // end call to SwingUtilities.invokeLater
         
      //myTurn = ( myMark.equals( X_MARK ) ); // determine if client's turn
      while ( ! isGameOver() )
      {
		  System.out.print("");
    	  while(!myTurn){
    		  try {
    			  Thread.sleep(1000);
    			  checkForTurn();
    		  } catch (InterruptedException e) {
    			  e.printStackTrace();
    		  }
    	  }    
          
      }// end while
   } // end method run
   
   public void checkForTurn(){
	   GetItemSpec spec = new GetItemSpec()
               .withPrimaryKey("PlayerId", playerId); 

	   try {
	        Item outcome = playerInfo.getItem(spec);
	        myTurn = outcome.getBOOL("IsTurn");
	        if(myTurn){
	        	String message  = outcome.getString("Message");
	        	processMessage( message );
	        }
	        else if(waitingForOpponent){
	        	GetItemSpec spec1 = new GetItemSpec()
	                    .withPrimaryKey("PlayerId", 2); 
	        	Item isConnected = playerInfo.getItem(spec1);
	        	if(isConnected.getBOOL("IsConnected")){
	        		UpdateItemSpec updateItemSpec = new UpdateItemSpec()
		                   								.withPrimaryKey("PlayerId", playerId)
		                   								.addAttributeUpdate(new AttributeUpdate("IsTurn").put(true));
	 	            UpdateItemOutcome itemOutcome = playerInfo.updateItem(updateItemSpec);
	 	            displayMessage("Player 2 has connected\n" +
	 	            			   "Your turn\n");
	 	            myTurn = true;
	 	            waitingForOpponent = false;
	        	}
	        }
	   }
	   catch (Exception e) {
           System.err.println("Unable to read item:" + playerId + " in checkForTurn");
           System.err.println(e.getMessage());
           displayMessage("your move");
       }

   }
   
   private boolean isGameOver() {
	  
	   return false;
   }

   private boolean checkBoard(int checkDirection){
	   int n = currentSquare.getSquareLocation();
	   //System.out.println(n);
	   int location = n + checkDirection;
	   int row = location / bsize; // calculate row
       int column = location % bsize; // calculate column
	   if(board[row][column].equals(myMark)){
		   //System.out.println(location);
		   location -= checkDirection;
		   row = location / bsize;
		   column = location % bsize;
		   if(board[row][column].equals(myMark)){
			   location -= checkDirection;
			   row = location / bsize;
			   column = location % bsize;
			   if(board[row][column].equals(myMark)){
				   return true;
			   }
			   location = n + checkDirection;
			   row = location / bsize;
		       column = location % bsize;
			   if(board[row][column].equals(myMark)){
				   return true;
			   }   
		   }
		   location = n + checkDirection;
		   row = location / bsize;
	       column = location % bsize;
		   if(board[row][column].equals(myMark)){
			   location = n + checkDirection;
			   row = location / bsize;
		       column = location % bsize;
		       if(board[row][column].equals(myMark)){
				   return true;
			   }
		   }
		}
	   if(board[row][column].equals(myMark)){
		   location += checkDirection;
		   row = location / bsize;
		   column = location % bsize;
		   if(board[row][column].equals(myMark)){
			   location += checkDirection;
			   row = location / bsize;
			   column = location % bsize;
			   if(board[row][column].equals(myMark)){
				   return true;
			   }
		   }
	   }
	   return false;
   }
   
   private void processMessage( String message )
   {
      // valid move occurred
      if ( message.equals( "Opponent Won" ) ) 
      {
         displayMessage( "Game over, Opponent won.\n" );
         // then highlight the winning locations down below.
         
      } // end if
      else if ( message.equals( "Opponent moved" ) ) 
      {
         int location = getOpponentMove(); // Here get move location from opponent
         								
         int row = location / bsize; // calculate row
         int column = location % bsize; // calculate column

         setMark(  board[ row ][ column ], 
            ( myMark.equals( X_MARK ) ? O_MARK : X_MARK ) ); // mark move                
         displayMessage( "Opponent moved. Your turn.\n" );
         myTurn = true; // now this client's turn
      } // end else if
      else
         displayMessage( message + "\n" ); // display the message
   } // end method processMessage

   //get move location from opponent
   private int getOpponentMove() {
	   GetItemSpec spec = new GetItemSpec()
               .withPrimaryKey("Row", "0"); 
	   Item outcome = gameInfo.getItem(spec);
	   int position = outcome.getInt("LastMove");
	   return position;
   }
   // manipulate outputArea in event-dispatch thread
   private void displayMessage( final String messageToDisplay )
   {
      SwingUtilities.invokeLater(
         new Runnable() 
         {
            public void run() 
            {
               displayArea.append( messageToDisplay ); // updates output
            } // end method run
         }  // end inner class
      ); // end call to SwingUtilities.invokeLater
   } // end method displayMessage

   // utility method to set mark on board in event-dispatch thread
   private void setMark( final Square squareToMark, final String mark )
   {
      SwingUtilities.invokeLater(
         new Runnable() 
         {
            public void run()
            {
            	squareToMark.setMark( mark ); // set mark in square
            } // end method run
         } // end anonymous inner class
      ); // end call to SwingUtilities.invokeLater
   } // end method setMark

   // Send message to cloud service indicating clicked square
   public void sendClickedSquare( int location )
   {
      // if it is my turn
      if ( myTurn ) 
      { 	  	
	       try {
	           //System.out.println("Attempting to read the item...");
	           UpdateItemSpec updateItemSpec;
	           UpdateItemOutcome itemOutcome;
	           
	           //Update the last move in the table
	           updateItemSpec = new UpdateItemSpec()
	                   	.withPrimaryKey("Row", "0")
	                   	.addAttributeUpdate(new AttributeUpdate("LastMove").put(location));
		       itemOutcome = gameInfo.updateItem(updateItemSpec);    
		       //Update the current player turns 
	           updateItemSpec = new UpdateItemSpec()
                   	.withPrimaryKey("PlayerId", playerId)
                   	.addAttributeUpdate(new AttributeUpdate("IsTurn").put(false));
	           itemOutcome = playerInfo.updateItem(updateItemSpec);
	           int otherPlayer;
	           if(playerId == 1){
	        	   otherPlayer = 2;
	           }
	           else{
	        	   otherPlayer = 1;
	           }
	           //Update the opponents turn
	           updateItemSpec = new UpdateItemSpec()
	                   	.withPrimaryKey("PlayerId", otherPlayer)
	                   	.addAttributeUpdate(new AttributeUpdate("IsTurn").put(true));
		       itemOutcome = playerInfo.updateItem(updateItemSpec);
		       String message = "Opponent moved";
		       //Checks to see if there is a winner and sets the field in the database
		       if(checkBoard(-15)  || checkBoard(-16) || checkBoard(-17) || checkBoard(-1)){
	        	   updateItemSpec = new UpdateItemSpec()
	                      	.withPrimaryKey("PlayerId", otherPlayer)
	                      	.addAttributeUpdate(new AttributeUpdate("OpponentWon").put(true));
	   	           itemOutcome = playerInfo.updateItem(updateItemSpec);
	   	           message = "Opponent Won";
		       }
		       updateItemSpec = new UpdateItemSpec()
	                   	.withPrimaryKey("PlayerId", otherPlayer)
	                   	.addAttributeUpdate(new AttributeUpdate("Message").put(message));
		       itemOutcome = playerInfo.updateItem(updateItemSpec);
	       } catch (Exception e) {
	           System.err.println("Unable to read item: " + playerId + "in sendClickedSquare");
	           System.err.println(e.getMessage());
	       }
    	  
         myTurn = false; // not my turn anymore
         
      } // end if
   } // end method sendClickedSquare

   // set current Square
   public void setCurrentSquare( Square square )
   {
      currentSquare = square; // set current square to argument
   } // end method setCurrentSquare

   // private inner class for the squares on the board
   private class Square extends JPanel 
   {
      private String mark; // mark to be drawn in this square
      private int location; // location of square
   
      public Square( String squareMark, int squareLocation )
      {
         mark = squareMark; // set mark for this square
         location = squareLocation; // set location of this square

         addMouseListener( 
            new MouseAdapter() {
               public void mouseReleased( MouseEvent e )
               {
            	   if(getMark().equals(" ")){
            	   if(myTurn){
	                  setCurrentSquare( Square.this ); // set current square
	                  TicTacToeClient.this.setMark( currentSquare, myMark );
	                  displayMessage("You clicked at location: " + getSquareLocation() + "\n");
	                  sendClickedSquare( getSquareLocation() );
            	   }
            	   }
                  //if(isValidMove()) // you have write your own method isValidMove().
                        //sendClickedSquare( getSquareLocation() );   
               } // end method mouseReleased
            } // end anonymous inner class
         ); // end call to addMouseListener
      } // end Square constructor

      // return preferred size of Square
      public Dimension getPreferredSize() 
      { 
         return new Dimension( 30, 30 ); // return preferred size
      } // end method getPreferredSize

      // return minimum size of Square
      public Dimension getMinimumSize() 
      {
         return getPreferredSize(); // return preferred size
      } // end method getMinimumSize

      // set mark for Square
      public void setMark( String newMark ) 
      { 
         mark = newMark; // set mark of square
         repaint(); // repaint square
      } // end method setMark
   
      // return Square location
      public int getSquareLocation() 
      {
         return location; // return location of square
      } // end method getSquareLocation
      
      public String getMark()
      {
    	  return mark;
      }
      // draw Square
      public void paintComponent( Graphics g )
      {
         super.paintComponent( g );

         g.drawRect( 0, 0, 29, 29 ); // draw square
         g.drawString( mark, 11, 20 ); // draw mark   
      } // end method paintComponent
   } // end inner-class Square
   
   

  public static void main( String args[] )
  {
     TicTacToeClient application; // declare client application

     // if no command line args
     if ( args.length == 0 )
        application = new TicTacToeClient( "" ); // 
     else
        application = new TicTacToeClient( args[ 0 ] ); // use args

     //application.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
     application.addWindowListener(new WindowListener() {            
    	  	@Override
	  	    public void windowOpened(WindowEvent e) {}	
    	    @Override public void windowClosing(WindowEvent e) {
    	    	try {
    		           UpdateItemSpec updateItemSpec;
    		           UpdateItemOutcome itemOutcome;
    		           updateItemSpec = new UpdateItemSpec()
    	                   	.withPrimaryKey("PlayerId", 1)
    	                   	.addAttributeUpdate(new AttributeUpdate("IsConnected").put(false))
    	                   	.addAttributeUpdate(new AttributeUpdate("IsTurn").put(false))
    	                   	.addAttributeUpdate(new AttributeUpdate("OpponentWon").put(false))
    		           		.addAttributeUpdate(new AttributeUpdate("Message").put("empty"));
    		           itemOutcome = playerInfo.updateItem(updateItemSpec);

    		           updateItemSpec = new UpdateItemSpec()
    		                   	.withPrimaryKey("PlayerId", 2)
    		                   	.addAttributeUpdate(new AttributeUpdate("IsConnected").put(false))
    		                   	.addAttributeUpdate(new AttributeUpdate("IsTurn").put(false))
    		                   	.addAttributeUpdate(new AttributeUpdate("OpponentWon").put(false))
    		                   	.addAttributeUpdate(new AttributeUpdate("Message").put("empty"));
    			       itemOutcome = playerInfo.updateItem(updateItemSpec);
    		       } catch (Exception ex) {
    		           System.err.println("Unable to read item: " + " window Listener");
    		           System.err.println(ex.getMessage());
    		       }
    	    }

    	    @Override public void windowIconified(WindowEvent e) {}            
    	    @Override public void windowDeiconified(WindowEvent e) {}            
    	    @Override public void windowDeactivated(WindowEvent e) {}            
    	    @Override public void windowActivated(WindowEvent e) {}
    	    @Override public void windowClosed(WindowEvent e) {}
    	});
  } // end main

} // end class TicTacToeClient




