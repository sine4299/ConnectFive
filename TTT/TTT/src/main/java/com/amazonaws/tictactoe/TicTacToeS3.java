package com.amazonaws.tictactoe;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.tictactoe.TicTacToeClient.Square;
import com.amazonaws.*;
import com.amazonaws.services.s3.model.DeleteObjectRequest;

//import aws
import com.amazonaws.client.builder.AwsClientBuilder;
import java.util.Iterator;
import java.util.List;

//Additions
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import java.io.Serializable;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public class TicTacToeS3 extends JFrame implements Runnable {
	
	/*If this gets messed up, local backup copy saved on Desktop*/
	
   	private static AmazonS3 myS3 = makeS3();
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
	private PlayerInfo playerInfo;
	private Board gameInfo;
	private int playerId;
	private static String myID;//if player is player one set this to player1key or vice-versa if not
	private String opponentID;//if player is player one set this to player2key or vice-versa if not
	private String player1key = "playerone";//Use this to store player one's serialized info in bucket
	private String player2key = "playertwo";//Use this to store player two's serialized info in bucket
	public static TicTacToeS3 appHandle = null;
	private String myBoard;

   	public static AmazonS3 makeS3() {
    	AWSCredentials credentials = null;
        try {
            credentials = new ProfileCredentialsProvider("default").getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                    "Please make sure that your credentials file is at the correct " +
                    "location (C:\\Users\\micah\\.aws\\credentials), and is in valid format.",
                    e);
        }
    	AmazonS3 s3client = new AmazonS3Client(credentials);
    	Region usWest2 = Region.getRegion(Regions.US_WEST_2);
        s3client.setRegion(usWest2);
    	return s3client;
    }
   	
   	public TicTacToeS3( String host ) { 
   		displayArea = new JTextArea( 4, 30 ); // set up JTextArea
   		displayArea.setEditable( false );
   		add( new JScrollPane( displayArea ), BorderLayout.SOUTH );
   		boardPanel = new JPanel(); // set up panel for squares in board
   		boardPanel.setLayout( new GridLayout( bsize, bsize, 0, 0 ) ); //was 3
   		board = new Square[ bsize ][ bsize ]; // create board

   		// loop over the rows in the board
   		for ( int row = 0; row < board.length; row++ ) {
   			// loop over the columns in the board
   			for ( int column = 0; column < board[ row ].length; column++ ) {
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
   	public void startClient() {  
   		setPlayerInfo();
   		// create and start worker thread for this client
   		ExecutorService worker = Executors.newFixedThreadPool( 1 );
   		worker.execute( this ); // execute client
   	} // end method startClient

   	public void setPlayerInfo() {
   		playerInfo = new PlayerInfo(1, false, false, "empty", false);
   		gameInfo = new Board();

 	    try {
 	        //Sets the player mark X for player 1  and O for player 2
 	        if(!checkExists("playerinfo.mnetz", "playeroneconnected", false)){
 	        	myMark =  X_MARK;
 	        	//The the player is the first to connect then they get first turn
 	        	waitingForOpponent = true;
 	        	//Updates the DB to say that is player is connected as player 1
 	        	playerInfo.PlayerId = 1;
 	        	myID = player1key;
 	        	opponentID = player2key;
 	        	myBoard = player1key + "board";
 	        	File dummyConnected = new File("playeroneconnected.txt");
 	        	dummyConnected.createNewFile();
 	        	//File savedResults = SerializeObject(playerInfo, "playerinfo.txt");
 	        	putObjectInS3("playerinfo.mnetz", "playeroneconnected", dummyConnected);
 	        	displayMessage("Player X connected\n");
 	        	displayMessage("Waiting for other player to connect\n");
 	        }
 	        else {
 	        	myMark = O_MARK;
 	        	//Updates the DB to say that is player is connected as player 2
 	        	playerInfo.PlayerId = 2;
 	        	myID = player2key;
 	        	opponentID = player1key;
 	        	myBoard = player2key + "board";
 	        	File dummyConnected = new File("playertwoconnected.txt");
 	        	dummyConnected.createNewFile();
 	        	File savedResults = SerializeObject(playerInfo, "playerone.txt");
 	        	putObjectInS3("playerinfo.mnetz", "playertwoconnected", dummyConnected);
 	        	putObjectInS3("playerinfo.mnetz", opponentID, savedResults);
 	        	displayMessage("Player O connected\n");
 	        	myTurn = false;
 	        }
 	    }
 	   	catch (Exception e) { System.err.println("Unable to read item: in setPlayerInfo" + e.getMessage()); }
   	}//end of setPlayerInfo
   	
   	public void putObjectInS3(String bucketName, String keyName, File uploadFile){
    	try{
    		System.out.println("Uploading a new object to S3 from a file\n");
            File file = uploadFile;
            myS3.putObject(new PutObjectRequest(bucketName, keyName, file));
    	}
    	catch (AmazonServiceException ase) {
            System.out.println("Error Message:    " + ase.getMessage());
            System.out.println("HTTP Status Code: " + ase.getStatusCode());
            System.out.println("AWS Error Code:   " + ase.getErrorCode());
            System.out.println("Error Type:       " + ase.getErrorType());
            System.out.println("Request ID:       " + ase.getRequestId());
        }
    	catch (AmazonClientException ace) { System.out.println("Error Message: " + ace.getMessage()); }
    }
   	
   	public void deleteObjectS3(String bucketName, String key){
   		try{
   			System.out.println("Deleting an object: " + key);
        	myS3.deleteObject(bucketName, key);
   		} catch (AmazonServiceException ase) {
        System.out.println("Error Message:    " + ase.getMessage());
        System.out.println("HTTP Status Code: " + ase.getStatusCode());
        System.out.println("AWS Error Code:   " + ase.getErrorCode());
        System.out.println("Error Type:       " + ase.getErrorType());
        System.out.println("Request ID:       " + ase.getRequestId());
    } catch (AmazonClientException ace) {
        System.out.println("Error Message: " + ace.getMessage());
    }
   	}
   	
   	public void deleteBucketS3(String bucketName){
   		try{
        	//Delete a bucket - A bucket must be completely empty before it can be deleted
   			ObjectListing object_listing = myS3.listObjects(bucketName);
        	List<S3ObjectSummary> summaries = object_listing.getObjectSummaries();
        	for (S3ObjectSummary summary : summaries) {
                String summaryKey = summary.getKey();
                deleteObjectS3(bucketName, summaryKey);
            }   	        
        	//System.out.println("Deleting bucket " + bucketName + "\n");
        	//myS3.deleteBucket(bucketName);
   		} catch (AmazonServiceException ase) {
	        System.out.println("Error Message:    " + ase.getMessage());
	        System.out.println("HTTP Status Code: " + ase.getStatusCode());
	        System.out.println("AWS Error Code:   " + ase.getErrorCode());
	        System.out.println("Error Type:       " + ase.getErrorType());
	        System.out.println("Request ID:       " + ase.getRequestId());
	    } catch (AmazonClientException ace) { System.out.println("Error Message: " + ace.getMessage()); }
   	}
   	
   	public static String ReadS3Content(String bucket, String key) {
   		StringBuilder sb = new StringBuilder();   	  
   		S3Object s3object = myS3.getObject(bucket, key);
   		InputStream is = s3object.getObjectContent();
   		BufferedReader br = new BufferedReader(new InputStreamReader(is));
   	  
   		String line;
   		try {
   			while((line = br.readLine()) != null) {
   				System.out.println(line);
   				sb.append(line);
   			}
   		} catch (IOException e) { e.printStackTrace(); }
   		return sb.toString();
   	}
   	
    public S3Object downloadObj(String bucketName, String key){
    	S3Object fetchFile = myS3.getObject(new GetObjectRequest(bucketName, key));
    	
    	InputStream objectData = fetchFile.getObjectContent();
    	try {
    		File f = new File(getPath().toString() + key);
    		Path target = f.toPath();
    		//Get newest version of file
    		Files.deleteIfExists(target);
    		Files.copy(objectData, target);
    		objectData.close();
    	}
    	catch(IOException exc) {System.out.println("Encountered IOException in downloadObj");}
    	return fetchFile;
    }
    
    /* The below method is passed the boolean shouldDelete, which indicates if the found file
    should be removed */
    public boolean checkExists(String bucketName, String key, boolean shouldDelete){    	
    	boolean results = myS3.doesObjectExist(bucketName, key);
    	if(results && shouldDelete){
    		deleteObjectS3(bucketName, key);
    	}
    	return results;
    }
    
    public boolean tryDownload(String bucketName, String key, File fileHandle){
    	S3Object fetchFile = myS3.getObject(new GetObjectRequest(bucketName, key));
    	
    	InputStream objectData = fetchFile.getObjectContent();
    	try {
    		fileHandle = new File(getPath().toString() + key);
    		Path target = fileHandle.toPath();
    		//Get newest version of file
    		Files.deleteIfExists(target);
    		Files.copy(objectData, target);
    		objectData.close();
    	}
    	catch(IOException exc) {System.out.println("Encountered IOException in downloadObj");
    		return false;
    	}
    	deleteObjectS3(bucketName, key);
    	return true;
    }
    
    public Path getPath(){
    	Path currentRelativePath = Paths.get("");
    	String s = currentRelativePath.toAbsolutePath().toString();
    	//System.out.println("Current relative path is: " + s);
    	return currentRelativePath;
    }
    
    public static File SerializeObject(Object obj, String fileName){
    	File resultsFile = null;
    	try {
    		resultsFile = new File(fileName);
			FileOutputStream f = new FileOutputStream(resultsFile);
			ObjectOutputStream o = new ObjectOutputStream(f);
			// Write objects to file
			o.writeObject(obj);
			o.close();
			f.close();
		}
    	catch (FileNotFoundException e) { System.out.println("File not found"); }
    	catch (IOException e) {	System.out.println("Error initializing stream"); }
    	return resultsFile;
	}
    
    public static PlayerInfo ReadSerializedPlayerInfo(String fileName){
    	PlayerInfo pi = null;
    	try{
	    	FileInputStream fi = new FileInputStream(new File(fileName));
			ObjectInputStream oi = new ObjectInputStream(fi);	
			// Read objects
			pi = (PlayerInfo) oi.readObject();	
			System.out.println(pi.toString());	
			oi.close();
			fi.close();
		}
    	catch (FileNotFoundException e) { System.out.println("File not found"); }
    	catch (IOException e) {	System.out.println("Error initializing stream"); }
    	catch (ClassNotFoundException e) { e.printStackTrace();	}
		return pi;
    }
    
    public static Board ReadSerializedBoard(String fileName){
    	Board b = null;
    	try {
	    	FileInputStream fi = new FileInputStream(new File(fileName));
			ObjectInputStream oi = new ObjectInputStream(fi);	
			// Read objects
			 b = (Board) oi.readObject();	
			oi.close();
			fi.close();	
		} 
    	catch (FileNotFoundException e) { System.out.println("File not found"); } 
    	catch (IOException e) {	System.out.println("Error initializing stream"); } 
    	catch (ClassNotFoundException e) { e.printStackTrace(); }
		return b;
    }
    
    public static class PlayerInfo implements Serializable
    {
    	private static final long serialVersionUID = 1L;
   		public int PlayerId;
   		public boolean IsConnected;
   		public boolean IsTurn;
   		public String message;
   		public boolean OpponentWon;
   		
   		public PlayerInfo(){
   			PlayerId = 1;
   			IsConnected = false;
   			IsTurn = false;
   			message = "empty";
   			OpponentWon = false;
   		}
   		public PlayerInfo(int i, boolean b1, boolean b2, String m, boolean b3){
   			PlayerId = i;
   			IsConnected = b1;
   			IsTurn = b2;
   			message = m;
   			OpponentWon = b3;
   		}
   		@Override
   		public String toString() {
   			return "PlayerId:" + PlayerId + "\nIsConnected: " + IsConnected 
   					+ "\nIsTurn: " + IsTurn + "\nmessage: " + message + "\nOpponentWon: " + OpponentWon;
   		}
   	}
    
    public static class Board implements Serializable
    {
   		public int Row;
   		public int LastMove;
   		
   		public Board(){
   			Row = 0;
   			LastMove = 0;
   		}
   		@Override
   		public String toString() {
   			return "Row:" + Row + "\nLastMove: " + LastMove;
   		}
   	}
    
    //private inner class for the squares on the board
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
	             	   if(/*myTurn*/checkExists("playerinfo.mnetz", myID, false)){
	             		   	  Square.this.setMark(myMark);
	    	                  setCurrentSquare( Square.this ); // set current square
	    	                  TicTacToeS3.this.setMark( currentSquare, myMark );
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

       public Dimension getPreferredSize() 
       { 
          return new Dimension(30, 30);
       } // end method getPreferredSize

       public Dimension getMinimumSize() 
       {
          return getPreferredSize(); // return preferred size
       } // end method getMinimumSize

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

    public void checkForTurn() {    	
    	try {
    		if(waitingForOpponent) {	        	
	        	if(checkExists("playerinfo.mnetz", opponentID + "connected", false)) {
	        		displayMessage("Player 2 has connected\nYour turn\n");
	        		myTurn = true;
	        		waitingForOpponent = false;
	        	}
	        }
    		if(checkExists("playerinfo.mnetz", myID, false)) {
    			myTurn = true;
    		}
    		else{
    			myTurn = false;
    		}
	        if(/*myTurn*/checkExists("playerinfo.mnetz", myID, false)){
	        	String message = "";
	        	ObjectListing object_listing = myS3.listObjects("message.mnetz");
	        	List<S3ObjectSummary> summaries = object_listing.getObjectSummaries();
	        	for (S3ObjectSummary summary : summaries) {
	                message += summary.getKey();
	            }	        	
	        	deleteBucketS3("message.mnetz");
	        	processMessage( message );	        	
	        }	        
	   }
	   catch (Exception e) {
		   System.err.println("Unable to read item:" + myID + " in checkForTurn\n" + e.getMessage());
		   displayMessage("*your move*\n");
	   }

    }

    private boolean isGameOver() {
 	   return checkExists("playerinfo.mnetz", "gameover", false);//Check if the gameover file exists
    }
    public boolean checkBoard(){
 		int row = 0;
 		int col = 0;
 		//Checks for vertical win
 		for(row = 0; row < 12; row++){
 			for(col = 0; col < 16; col++){
 				if(!board[row][col].getMark().equals(" ") &&
 				   board[row][col].getMark().equals(board[row + 1][col].getMark()) && 
 				   board[row][col].getMark().equals(board[row + 2][col].getMark()) && 
 				   board[row][col].getMark().equals(board[row + 3][col].getMark()) && 
 				   board[row][col].getMark().equals(board[row + 4][col].getMark())){
 				 		return true;	  	
 				}
 			}
 		}
 		//Checks for horizontal win
 		for(col = 0; col < 12; col++){
 			for(row = 0; row < 16; row++){
 				if(!board[row][col].getMark().equals(" ") &&
 				   board[row][col].getMark().equals(board[row][col + 1].getMark()) && 
 				   board[row][col].getMark().equals(board[row][col + 2].getMark()) && 
 				   board[row][col].getMark().equals(board[row][col + 3].getMark()) && 
 				   board[row][col].getMark().equals(board[row][col + 4].getMark())){
 				 		return true;	  	
 				}
 			}
 		}
 		for(row = 0; row < 5; row++){
 			for(col = 0; col < 13; col++){
 				if(!board[row][col].getMark().equals(" ") &&
 				   board[row][col].getMark().equals(board[row + 1][col + 1].getMark()) && 
 				   board[row][col].getMark().equals(board[row + 2][col + 2].getMark()) && 
 				   board[row][col].getMark().equals(board[row + 3][col + 3].getMark()) && 
 				   board[row][col].getMark().equals(board[row + 4][col + 4].getMark())){
 				 		return true;	  	
 				}
 			}
 		}
 		for(row = 6; row < 15; row++){
 			for(col = 0; col < 13; col++){
 				if(!board[row][col].getMark().equals(" ") &&
 				   board[row][col].getMark().equals(board[row - 1][col + 1].getMark()) && 
 				   board[row][col].getMark().equals(board[row - 2][col + 2].getMark()) && 
 				   board[row][col].getMark().equals(board[row - 3][col + 3].getMark()) && 
 				   board[row][col].getMark().equals(board[row - 4][col + 4].getMark())){
 				 		return true;	  	
 				}
 			}
 		}
 		return false;
 	}
  	//get move location from opponent
    private int getOpponentMove() {
    	int position = 0;    	
    	ObjectListing object_listing = myS3.listObjects("board.mnetz");
    	List<S3ObjectSummary> summaries = object_listing.getObjectSummaries();
    	for (S3ObjectSummary summary : summaries) {
            position += Integer.parseInt(summary.getKey());
        }
    	deleteBucketS3("board.mnetz");
    	return position;
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
    	//Might only need one or the other (or &&) as both may confuse things
       if ( /*myTurn || */checkExists("playerinfo.mnetz", myID, false) )
       { 	  	
	       try {
	    	   File dummyMove = new File("move.txt");
	    	   dummyMove.createNewFile();
	    	   putObjectInS3("board.mnetz", Integer.toString(location), dummyMove);
	           int otherPlayer;
	           if(playerId == 1){ otherPlayer = 2; }
	           else { otherPlayer = 1; }
	           //Update the opponents turn           
		       String message = "Opponent moved";
		       
		       //Checks to see if there is a winner and sets the field in the database
		       //if(checkBoard(-15)  || checkBoard(-16) || checkBoard(-17) || checkBoard(-1)){
		       if(checkBoard()){
		    	   displayMessage("You Won");
		    	   File dummyWinner = new File("gameover.txt");
		    	   dummyWinner.createNewFile();
		    	   putObjectInS3("playerinfo.mnetz", "gameover", dummyWinner);
	   	           message = "Opponent Won";
		       }
		       File dummyMessage = new File("message.txt");
		       dummyMessage.createNewFile();
		       putObjectInS3("message.mnetz", message, dummyMessage);
	       } 
	       catch (Exception e) {
	           System.err.println("Unable to read item: " + myID + " in sendClickedSquare\n" + e.getMessage());
	       }
 	  
	       //myTurn = false; // not my turn anymore
	       checkExists("playerinfo.mnetz", myID, true);//Delete current player file if exists
	       File f = SerializeObject(playerInfo, opponentID + ".txt");
	       putObjectInS3("playerinfo.mnetz", opponentID, f);
          
       } // end if
    } // end method sendClickedSquare

    // set current Square
    public void setCurrentSquare( Square square ) {
       currentSquare = square; // set current square to argument
    } // end method setCurrentSquare
    
    public static void main(String[] args) throws IOException {    	
    	TicTacToeS3 application; // declare client application
        // if no command line args
        if ( args.length == 0 ){ application = new TicTacToeS3("");}
        else{ application = new TicTacToeS3(args[0]); }// use args
        appHandle = application;
        //application.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
        application.addWindowListener(new WindowListener() {            
       	  	@Override public void windowOpened(WindowEvent e) {}	
       	    @Override public void windowClosing(WindowEvent e) {
       	    	try { CleanUp(); }
       	    	catch (Exception ex) { System.err.println("Unable to read item: window Listener\n" + ex.getMessage()); }
       	    }
       	    @Override public void windowIconified(WindowEvent e) {}            
       	    @Override public void windowDeiconified(WindowEvent e) {}            
       	    @Override public void windowDeactivated(WindowEvent e) {}            
       	    @Override public void windowActivated(WindowEvent e) {}
       	    @Override public void windowClosed(WindowEvent e) { }
       	});        
    }
    private static void CleanUp() {
    	appHandle.deleteObjectS3("playerinfo.mnetz", myID + "connected");
    	appHandle.checkExists("playerinfo.mnetz", myID, true);
    	appHandle.checkExists("playerinfo.mnetz", "gameover", true);
    	appHandle.deleteBucketS3("board.mnetz");
    	appHandle.deleteBucketS3("message.mnetz");
	}

// control thread that allows continuous update of displayArea
    public void run() {
       SwingUtilities.invokeLater( 
          new Runnable() {         
             public void run() {
                // display player's mark
                idField.setText( "You are player \"" + myMark + "\"" );
             } // end method run
          } // end anonymous inner class
       ); // end call to SwingUtilities.invokeLater
          
       //myTurn = ( myMark.equals( X_MARK ) ); // determine if client's turn
       while ( ! isGameOver() ) {
    		  System.out.print("");
     	  while(/*!myTurn*/!checkExists("playerinfo.mnetz", myID, false)) {
     		  try {
     			  Thread.sleep(500);
     			  checkForTurn();
     		  } 
     		  catch (InterruptedException e) { e.printStackTrace(); }
     	  }           
       }// end while
    } // end method run
}