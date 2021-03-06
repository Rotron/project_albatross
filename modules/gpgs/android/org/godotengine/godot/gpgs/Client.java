package org.godotengine.godot.gpgs;

import android.content.Intent;
import android.app.Activity;
import android.support.annotation.NonNull;
import android.util.Log;

import org.godotengine.godot.GodotLib;
import org.godotengine.godot.gpgs.PlayerInfo;
import org.godotengine.godot.GodotPlayGameServices;

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.games.Games;
import com.google.android.gms.games.Player;
import com.google.android.gms.games.PlayersClient;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.games.GamesActivityResultCodes;

public class Client {

    private static final String TAG = "gpgs";

    private static final int SIGN_IN_SILENT = 0;
    private static final int SIGN_IN_INTERACTIVE = 1;

    private static final String[] GODOT_CALLBACK_FUNCTIONS = new String[] {
            "_on_play_game_services_sign_in_success", //(int signInType, String playerID)
            "_on_play_game_services_player_info_failure", //(int signInType)
            "_on_play_game_services_sign_in_failure", //(int signInType)
            "_on_play_game_services_sign_out", //(boolean success)
    };

    // Request code used to invoke sign in user interactions.
    private static final int RC_SIGN_IN = 9001;
    private static final int RC_WAITING_ROOM = 9007;

    // Client used to sign in with Google APIs
    private GoogleSignInClient mGoogleSignInClient = null;

    // The currently signed in account, used to check the account has changed outside of this activity when resuming.
    GoogleSignInAccount mSignedInAccount = null;

    // Godot instance id needed to provide callback functionality in GDScript
    private int instance_id = 0;

    // The activity that this code will run in when the game is deployed
    private Activity activity = null;

    // The main class object for this module
    private GodotPlayGameServices gpgs = null;

    public PlayerInfo currentPlayer = null;

    public Client(final Activity activity, final int instance_id, GodotPlayGameServices gpgs, boolean buildSnapshots) {
        this.instance_id = instance_id;
        this.activity = activity;
        this.gpgs = gpgs;

        Log.d(TAG, "Client()");

        // Create the client used to sign in.
        if (buildSnapshots){
            Log.d(TAG, "Creating sign in client with Saved Games functionality");
            GoogleSignInOptions signInOptions = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_GAMES_SIGN_IN)
                    .requestScopes(Drive.SCOPE_APPFOLDER)
                    .build();
            mGoogleSignInClient = GoogleSignIn.getClient(activity, signInOptions);
        }else{
            Log.d(TAG, "Creating sign in client");
            mGoogleSignInClient = GoogleSignIn.getClient(activity, GoogleSignInOptions.DEFAULT_SIGN_IN);
        }
    }

    /**
     * Start a sign in activity.
     */
    public void signInInteractive() {
        Log.d(TAG, "signInInteractive()");
        activity.startActivityForResult(mGoogleSignInClient.getSignInIntent(), RC_SIGN_IN);
    }


    /**
     * Try to sign in without displaying dialogs to the user.
     * If the user has already signed in previously, it will not show dialog.
     */
    public void signInSilent() {
        Log.d(TAG, "signInSilent()");
        mGoogleSignInClient.silentSignIn().addOnCompleteListener(activity, new OnCompleteListener<GoogleSignInAccount>() {
            @Override
            public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                if (task.isSuccessful()) {
                    Log.d(TAG, "signInSilently(): success");
                    onConnected(task.getResult(), SIGN_IN_SILENT);
                } else {
                    Log.d(TAG, "signInSilently(): failure", task.getException());
                    onDisconnected();
                    GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[2], new Object[] { SIGN_IN_SILENT });
                }
            }
        });
    }

    public void signOut() {
        Log.d(TAG, "signOut()");

        mGoogleSignInClient.signOut().addOnCompleteListener(activity, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    Log.d(TAG, "signOut(): success");
                    onDisconnected();
                    GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[3], new Object[] { true });
                } else {
                    int code = ((ApiException) task.getException()).getStatusCode();
                    Log.d(TAG, "signOut() failed with API Exception status code: " + code);
                    GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[3], new Object[] { false });
                }
            }
        });
    }

    public void onMainActivityResult(int requestCode, int resultCode, Intent intent) {
        // super.onActivityResult(requestCode, resultCode, data);
    
        // Result returned from launching the Intent from GoogleSignInClient.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            // The Task returned from this call is always completed, no need to attach
            // a listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(intent);
            handleSignInResult(task);
        } else if (requestCode == RC_WAITING_ROOM) {

            // we got the result from the "waiting room" UI.
      
            if (resultCode == Activity.RESULT_OK) {
      
              // ready to start playing
      
              Log.d(TAG, "... (waiting room returned OK).");
      
              //startGame(true);
      
            } else if (resultCode == GamesActivityResultCodes.RESULT_LEFT_ROOM) {
      
              // player indicated that they want to leave the room
      
              gpgs.rtmLeaveRoom();
      
            } //else if (resultCode == Activity.RESULT_CANCELED) {
      
              // Dialog was cancelled (user pressed back key, for instance). In our game,
      
              // this means leaving the room too. In more elaborate games, this could mean
      
              // something else (like minimizing the waiting room UI).
      
              //leaveRoom();
        }
        
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            
            onConnected(account, SIGN_IN_INTERACTIVE);
            // Signed in successfully, show authenticated UI.
            //updateUI(account);
        } catch (ApiException e) {
            // The ApiException status code indicates the detailed failure reason.
            // Please refer to the GoogleSignInStatusCodes class reference for more information.
            Log.w(TAG, "signInResult:failed code=" + e.getStatusCode());
            //updateUI(null);
            onDisconnected();
            GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[2], new Object[] { SIGN_IN_INTERACTIVE });
        }
    }

    private void onConnected(GoogleSignInAccount googleSignInAccount, int signInType) {
        Log.d(TAG, "onConnected(): connected to Google APIs");
        if (mSignedInAccount != googleSignInAccount) {
            mSignedInAccount = googleSignInAccount;
            gpgs.setClient(mSignedInAccount);
            getSignedInPlayer(signInType);
        }
    }

    public void onDisconnected() {
        Log.d(TAG, "onDisconnected()");
        mSignedInAccount = null;
        currentPlayer = null;
        gpgs.removeClient();
    }

    public void getSignedInPlayer(final int signInType){
        PlayersClient playersClient = Games.getPlayersClient(activity, mSignedInAccount);
        playersClient.getCurrentPlayer().addOnSuccessListener(new OnSuccessListener<Player>() {
            @Override
            public void onSuccess(Player p) {
                currentPlayer = new PlayerInfo(activity, instance_id, p);
                Log.d(TAG, p.getPlayerId());

                GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[0], new Object[] { signInType, p.getPlayerId() });
            }
        })
        .addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG,"There was a problem getting the player id!");
                GodotLib.calldeferred(instance_id, GODOT_CALLBACK_FUNCTIONS[1], new Object[] { signInType });
            }
        });
    }
}