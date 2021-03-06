package votebot;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.exception.IrcException;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.ConnectEvent;
import org.pircbotx.hooks.events.DisconnectEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Scanner;

public class VoteBot extends ListenerAdapter{
	
	public static final String votesPath = "votes.txt";
	public static final String top50Path = "top50.txt";

	public static File votesTxt;
	public static String threadURL;
	private static ArrayList<String> votes = new ArrayList<String>();
	private static HashSet<String> top50 = new HashSet<String>();
	
	/* 
	 * case we want:
	 * <user> ?vote -GN;CXu;Sebu;Afrodafro;
	 * <-GN> bot reply: vote registered, thank you
	 * <user> ?vote -GN;-Aknano;xhale;314159265358979;Gizu;Pozepi;
	 * <-GN> bot reply: old vote discarded, new vote registered. old vote was -GN;CXu;Sebu;Afrodafro;
	 *  
	 * alternatively:
	 * <user> ?vote -GN;notintop50;notintop50either;FERRETu;Fendon;
	 * <-GN> bot reply: users at #2, #3 were not found in the top 50. vote discarded - please follow instructions in [<thread link> the voting thread] closely!
	 * <user> what the fuck, he is totally pro and stuff
	 * <-GN> get it right fuckwit
	 * 
	 * alternatively 2:
	 * <user> ?vote -GN;Sebu;-PC;CXu;
	 * <-GN> bot reply: you put yourself at #3. vote discarded - please follow instructions in [<thread link> the voting thread] closely!
	 * <user> RUDE
	 * <-GN> lmao
	 * 
	 * alternatively 3:
	 * <user> ?vote -GN;CXu;-PC;CXu;
	 * <-GN> bot reply: duplicate votes detected - check votes at #2, #4. vote discarded - please follow instructions in [<thread link> the voting thread] closely!
	 * <user> oh fuck
	 *  
	 * help:
	 * <user> ?vote/<user> ?vote help/<user> ?vote ashjdgjkshgjkdf
	 * <-GN> bot reply: instructions can be found in [<thread link> the voting thread]. thanks for showing interest!
	 */
	
	@Override
	public void onConnect(ConnectEvent event) {
		log("connected!");
	}
	@Override
	public void onDisconnect(DisconnectEvent event) {
		log("disconnected! wtf");
	}
	
	@Override
	public void onPrivateMessage(PrivateMessageEvent event) {
		try {
			//	receive
			String message = event.getMessage();
			String sender = event.getUser().getNick();
			
			String reply = "default message. if you see this -GN fucked up :(";

	    	// case for ?vote - only command we need for this nice bot
			if (message.split(" ")[0].equalsIgnoreCase("?vote")) {
				log("<" + sender + "> " + message);
				
				if(message.split(" ").length > 1 && message.split(";").length > 1){ // if ?vote has players parameter
					
					message = message.substring(6); // remove "?vote ", old .split(" ")[1] method didn't work out
					String[] votedFor = message.toLowerCase().replace(' ', '_').split(";", 11);	// max limit of 11
					
					//	cut away
					if(votedFor.length == 11){
						String[] tempArray = new String[10];
						System.arraycopy(votedFor, 0, tempArray, 0, 10);
						
						votedFor = tempArray;
					}
					
					boolean notFoundError = false;
					boolean[] foundInTop50Indexes = new boolean[votedFor.length]; // small note: defaults to false
					boolean votedForYourselfError = false;
					int votedForYourselfIndex = 0;
					boolean duplicateVoteError = false;
					boolean[] duplicateVoteIndexes = new boolean[votedFor.length];

					////////////////////////////////////////////////////////////////// check for errors
										
					for(int i = 0; i < votedFor.length; i++){
						if(i >= 10)
							break;
						
						if(top50.contains(votedFor[i].trim()))
							foundInTop50Indexes[i] = true;
						else
							notFoundError = true;
						
						if(votedFor[i].equalsIgnoreCase(sender)){
							votedForYourselfError = true;
							votedForYourselfIndex = i;
						}
						
						for(int j = i + 1; j < votedFor.length; j++){
							if(j >= 10)
								continue;
							
							if(votedFor[i].equalsIgnoreCase(votedFor[j])){
								duplicateVoteIndexes[i] = true;
								duplicateVoteIndexes[j] = true;
								duplicateVoteError = true;
							}
						}
					}

					////////////////////////////////////////////////////////////////// feedback reply construction
					// done with priority first to last - only sends one reply at a time even if multiple things are wrong!
					
					if(notFoundError){
						// construct reply string containing users that weren't found
						int count = 0;
						StringBuilder wrongIndexes = new StringBuilder();
						
						for(int i = 0; i < foundInTop50Indexes.length; i++){
							if(!foundInTop50Indexes[i]){
								wrongIndexes.append("#" + (i + 1) + ", ");
								count++;
							}
						}
						wrongIndexes = wrongIndexes.delete(wrongIndexes.length() - 2, wrongIndexes.length()); // cut away final ", "
						
						reply = "user" + (count != 1 ? "s" : "") + " at " + wrongIndexes + (count != 1 ? " were" : " was") + " not found in the top 50. vote discarded - please follow instructions in [" + threadURL + " the voting thread] closely!";
					}
					else if(votedForYourselfError){
						reply = "you put yourself at #" + (votedForYourselfIndex + 1) + ". vote discarded - please follow instructions in [" + threadURL + " the voting thread] closely!";
					}
					else if(duplicateVoteError){
						// construct reply string containing indexes for duplicate users
						StringBuilder duplicateIndexes = new StringBuilder();
						
						for(int i = 0; i < duplicateVoteIndexes.length; i++){
							if(duplicateVoteIndexes[i]){
								duplicateIndexes.append("#" + (i + 1) + ", ");
							}
						}
						duplicateIndexes = duplicateIndexes.delete(duplicateIndexes.length() - 2, duplicateIndexes.length()); // cut away final ", "
						
						reply = "duplicate votes detected - check votes at " + duplicateIndexes + ". vote discarded - please follow instructions in [" + threadURL + " the voting thread] closely!";
					}
					// more error handling?
					// if everything is ok, move on to saving to file!
					else {
						
					    // first remove eventual excessive entries from original message
				    	StringBuilder cutMessage = new StringBuilder();
				    	String[] voteArray = message.split(";");
				    	
				    	for(int i = 0; i < (voteArray.length > 10? 10 : voteArray.length); i++){
				    		cutMessage.append(voteArray[i] + "; ");
				    	}
				    	
						boolean hasVoted = false;
						
						for(String vote : votes) {
						    if(vote.startsWith(sender)) {
						    	// remove old vote
						    	hasVoted = true;
							    votes.remove(vote);
						    	
								// register new vote
						    	updateVoteList(sender + "/" + cutMessage + "\n");
						    	
						    	reply = "old vote discarded, new vote registered. old vote was " + vote.substring(vote.indexOf('/') + 1);

						    	break;
						    }
						}

						if(!hasVoted){
							// register vote	
					    	updateVoteList(sender + "/" + cutMessage);		
							reply = "vote registered, thank you";
						}
					}
				}
				else { // if ?vote parameter doesn't exist or is wrong
					reply = "instructions for voting can be found in [" + threadURL + " the voting thread]. thanks for showing interest!";
				}

				//	send
				event.respond("bot reply: " + reply);
				log("to " + sender + ": <-GN> bot reply: " + reply);
			}

			Thread.sleep(500); // flow control :^)
		}
		catch (Exception e){
			event.respond("bot reply: oops! something terrible happened and i can't process your vote. fire -GN a PM on the website and he'll figure it out, probably.");
			
			// no idea what might occur, but now we can find out.
			log("Exception occured: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	/**
	 * Initializes the votebot. Call this method to start running it.
	 * 
	 * @param config	PircBotX Configuration to connect with
	 * @param url		thread link that displays in feedback messages.
	 * @throws IOException
	 * @throws IrcException
	 */
	public static void initialize(Configuration config, String url) throws IOException, IrcException {
		// if you're gonna use this, look up on PircBotX's Configuration class - it needs a setup to work.
		// also get your IRC password from osu.ppy.sh/p/irc and a thread link you can redirect people to to explain wtf is going on
		threadURL = url;
		
		// create file if doesn't exist
		votesTxt = new File(votesPath);
		votesTxt.createNewFile();
		
		// for some dumb fucking reason, probably related to peppy's fucking irc setup, any quit message throws a fucking nullpointerexception
		Logger root = (Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
		// so we make the logger shut the fuck up and rely on a helper method
		root.setLevel(Level.OFF);
		
		// current (static) top 50 list from file to array
		Scanner read = new Scanner(new FileReader(top50Path));
		while(read.hasNextLine())
			top50.add(read.nextLine().toLowerCase());
		
		// initialize saved votes to arraylist
		read = new Scanner(new FileReader(votesPath));
		while(read.hasNextLine()){
			String line = read.nextLine();
			
			// this is all i have in terms of verification
			if(line.contains("/"))
				votes.add(line);
		}
		
		read.close();
		
		////////////////////////////////////////////////////////////////// start bot
		
		@SuppressWarnings("resource")
		PircBotX bot = new PircBotX(config);
		
		log("connecting!");
		bot.startBot();
	}
	
	// helper for adding/updating votes in file "votes.txt"
	private static void updateVoteList(String newVote) throws IOException {
		votes.add(newVote);
		
		votesTxt.delete();
		votesTxt = new File(votesPath);
		votesTxt.createNewFile();

		FileWriter out = new FileWriter(votesTxt);
		for(String vote : votes){
			out.write(vote + "\n");
		}
		out.close();
	}
	
	// helper for printing to console
	private static void log(String str){
		System.out.println("[votebot] " + new Date(System.currentTimeMillis()) + " - " + str);
	}
}