/*
 * GestureTrainer.java
 *
 * Created: 18.08.2011
 *
 * Copyright (C) 2011 Robert Nesselrath
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package de.dfki.android.gestureTrainer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.util.PebbleDictionary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.UUID;

import de.dfki.android.gesture.R;
import de.dfki.ccaal.gestures.IGestureRecognitionListener;
import de.dfki.ccaal.gestures.IGestureRecognitionService;
import de.dfki.ccaal.gestures.classifier.Distribution;
import de.dfki.pebble.PebbleBlehBluhBlah;

public class GestureTrainer extends Activity {

	IGestureRecognitionService recognitionService;
	String activeTrainingSet;

	private final ServiceConnection serviceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			recognitionService = IGestureRecognitionService.Stub.asInterface(service);
			try {
				recognitionService.startClassificationMode(activeTrainingSet);
				recognitionService.registerListener(IGestureRecognitionListener.Stub.asInterface(gestureListenerStub));
			} catch (RemoteException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName className) {
			recognitionService = null;
		}
	};

	IBinder gestureListenerStub = new IGestureRecognitionListener.Stub() {

		@Override
		public void onGestureLearned(String gestureName) throws RemoteException {
			Toast.makeText(GestureTrainer.this, String.format("Gesture %s learned", gestureName), Toast.LENGTH_SHORT).show();
			System.err.println("Gesture %s learned");
		}

		@Override
		public void onTrainingSetDeleted(String trainingSet) throws RemoteException {
			Toast.makeText(GestureTrainer.this, String.format("Training set %s deleted", trainingSet), Toast.LENGTH_SHORT).show();
			System.err.println(String.format("Training set %s deleted", trainingSet));
		}

		@Override
		public void onGestureRecognized(final Distribution distribution) throws RemoteException {
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(GestureTrainer.this, String.format("%s: %f", distribution.getBestMatch(), distribution.getBestDistance()), Toast.LENGTH_LONG).show();
					sendDataToServer task = new sendDataToServer();
					task.execute(distribution.getBestMatch());
					System.err.println(String.format("%s: %f", distribution.getBestMatch(), distribution.getBestDistance()));
				}
			});
		}
	};
	private final static UUID PEBBLE_APP_UUID = UUID.fromString("3c989451-0e2b-4ef1-8ccd-9761b130003a");


	private PebbleKit.PebbleDataReceiver receiver;

//	@Override
//	protected void onCreate(Bundle savedInstanceState) {
//		super.onCreate(savedInstanceState);
//
//		startButton = (Button)findViewById(R.id.start_button);
//
//		startButton.setOnClickListener(new OnClickListener() {
//
//			@Override
//			public void onClick(View v) {
//				PebbleDictionary dict = new PebbleDictionary();
//				dict.addInt32(0, 0);
//				PebbleKit.sendDataToPebble(getApplicationContext(), uuid, dict);
//			}
//
//		});
//	}

	private static final UUID uuid = UUID.fromString("2893b0c4-2bca-4c83-a33a-0ef6ba6c8b17");
	private static final int NUM_SAMPLES = 15;

		/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		Toast.makeText(this, PebbleKit.isWatchConnected(this) ? "Connected": "Nopie", Toast.LENGTH_SHORT).show();
		Toast.makeText(this, PebbleKit.areAppMessagesSupported(this) ? "supported": "Not supported", Toast.LENGTH_SHORT).show();

		PebbleDictionary dict = new PebbleDictionary();
		dict.addInt32(0, 0);
		PebbleKit.sendDataToPebble(getApplicationContext(), uuid, dict);

//		PebbleKit.registerReceivedDataHandler(this, new PebbleKit.PebbleDataReceiver(PEBBLE_APP_UUID) {
//
//			@Override
//			public void receiveData(final Context context, final int transactionId, final PebbleDictionary data) {
//				Log.i(getLocalClassName(), "Received value=" + " " + data.getBytes(0) + " for key: 0");
//				Log.i(getLocalClassName(), "Received value=" + " " + data.getBytes(1) + " for key: 1");
//				Log.i(getLocalClassName(), "Received value=" + " " + data.getBytes(2) + " for key: 2");
//				Log.i(getLocalClassName(), "Received data=" + data.toJsonString());
//				PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);
//			}
//
//		});


		receiver = new PebbleKit.PebbleDataReceiver(uuid) {

			@Override
			public void receiveData(Context context, int transactionId, PebbleDictionary data) {
				PebbleKit.sendAckToPebble(getApplicationContext(), transactionId);

				int[] latest_data = new int[3 * NUM_SAMPLES];
//				Log.d(TAG, "NEW DATA PACKET");
				for(int i = 0; i < NUM_SAMPLES; i++) {
					for(int j = 0; j < 3; j++) {
						try {
							latest_data[(3 * i) + j] = data.getInteger((3 * i) + j).intValue();
						} catch(Exception e) {
							latest_data[(3 * i) + j] = -1;
						}
					}
//					Log.d(TAG, "Sample " + i + " data: X: " + latest_data[(3 * i)] + ", Y: " + latest_data[(3 * i) + 1] + ", Z: " + latest_data[(3 * i) + 2]);
				}
				StringBuilder sb = new StringBuilder("[");
				for(int i = 0; i < latest_data.length; i++)
				{
					sb.append(Integer.toString(latest_data[i]) + ", ");
				}
				sb.append(']');
//				Log.e("Data", sb.toString());

				if(latest_data[0] != 0 && latest_data[1] != 0 && latest_data[2] != 0)
				{
					PebbleBlehBluhBlah.events.add(new int[]{latest_data[0], latest_data[1], latest_data[2]});
					PebbleBlehBluhBlah.events.add(new int[]{latest_data[3], latest_data[4], latest_data[5]});
					PebbleBlehBluhBlah.events.add(new int[]{latest_data[6], latest_data[7], latest_data[8]});
					PebbleBlehBluhBlah.events.add(new int[]{latest_data[9], latest_data[10], latest_data[11]});
					PebbleBlehBluhBlah.events.add(new int[]{latest_data[12], latest_data[13], latest_data[14]});

				}

			}

		};

		PebbleKit.registerReceivedDataHandler(this, receiver);



		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		final TextView activeTrainingSetText = (TextView) findViewById(R.id.activeTrainingSet);
		final EditText trainingSetText = (EditText) findViewById(R.id.trainingSetName);
		final EditText editText = (EditText) findViewById(R.id.gestureName);
		activeTrainingSet = editText.getText().toString();
		final Button startTrainButton = (Button) findViewById(R.id.trainButton);
		final Button deleteTrainingSetButton = (Button) findViewById(R.id.deleteTrainingSetButton);
		final Button changeTrainingSetButton = (Button) findViewById(R.id.startNewSetButton);
		final SeekBar seekBar = (SeekBar) findViewById(R.id.seekBar1);
		seekBar.setVisibility(View.INVISIBLE);
		seekBar.setMax(20);
		seekBar.setProgress(20);
		seekBar.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

				try {
					recognitionService.setThreshold(progress / 10.0f);
				} catch (RemoteException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
		});

		startTrainButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if (recognitionService != null) {
					try {
						if (!recognitionService.isLearning()) {
							startTrainButton.setText("Stop Training");
							editText.setEnabled(false);
							deleteTrainingSetButton.setEnabled(false);
							changeTrainingSetButton.setEnabled(false);
							trainingSetText.setEnabled(false);
							recognitionService.startLearnMode(activeTrainingSet, editText.getText().toString());
						} else {
							startTrainButton.setText("Start Training");
							editText.setEnabled(true);
							deleteTrainingSetButton.setEnabled(true);
							changeTrainingSetButton.setEnabled(true);
							trainingSetText.setEnabled(true);
							recognitionService.stopLearnMode();
						}
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});
		changeTrainingSetButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View arg0) {
				activeTrainingSet = trainingSetText.getText().toString();
				activeTrainingSetText.setText(activeTrainingSet);

				if (recognitionService != null) {
					try {
						recognitionService.startClassificationMode(activeTrainingSet);
					} catch (RemoteException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		});

		deleteTrainingSetButton.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

				AlertDialog.Builder builder = new AlertDialog.Builder(GestureTrainer.this);
				builder.setMessage("You really want to delete the training set?").setCancelable(true).setPositiveButton("Yes", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						if (recognitionService != null) {
							try {
								recognitionService.deleteTrainingSet(activeTrainingSet);
							} catch (RemoteException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}
				}).setNegativeButton("No", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.cancel();
					}
				});
				builder.create().show();
			}
		});
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.options_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.edit_gestures:
			Intent editGesturesIntent = new Intent().setClass(this, GestureOverview.class);
			editGesturesIntent.putExtra("trainingSetName", activeTrainingSet);
			startActivity(editGesturesIntent);
			return true;

		default:
			return false;
		}
	}

	@Override
	protected void onPause() {
		try {
			recognitionService.unregisterListener(IGestureRecognitionListener.Stub.asInterface(gestureListenerStub));
		} catch (RemoteException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		recognitionService = null;
//		unregisterReceiver(receiver);
		unbindService(serviceConnection);
		super.onPause();
	}

	@Override
	protected void onResume() {
		Intent bindIntent = new Intent("de.dfki.ccaal.gestures.GESTURE_RECOGNIZER");
		bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE);
		super.onResume();
	}

	public class sendDataToServer extends AsyncTask<String, Void, Void> {
			@Override
			protected Void doInBackground(String... params) {
				// These two need to be declared outside the try/catch
				// so that they can be closed in the finally block.
				HttpURLConnection urlConnection = null;
				BufferedReader reader = null;

				String temp = params[0];
				String code = temp.substring(7);

				// Will contain the raw JSON response as a string.
				String forecastJsonStr = null;

				try {
					Log.v("TRYING", "SEDNIGN GADATA");
					// Construct the URL for the OpenWeatherMap query
					// Possible parameter
					// s are avaiable at OWM's forecast API page, at
					// http://openweathermap.org/API#forecast
					URL url = new URL("http://192.168.43.42:8000/setGesture?q=" + code);

					// Create the request to OpenWeatherMap, and open the connection
					Log.v("TRYING", "Sending Data");
					urlConnection = (HttpURLConnection) url.openConnection();
					urlConnection.setRequestMethod("GET");

					urlConnection.connect();
					Log.v("TRYING", "Sent");
					// Read the input stream into a String
					InputStream inputStream = urlConnection.getInputStream();
					StringBuffer buffer = new StringBuffer();
					if (inputStream == null) {
						// Nothing to do.
						return null;
					}
					reader = new BufferedReader(new InputStreamReader(inputStream));

					String line;
					while ((line = reader.readLine()) != null) {
						// Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
						// But it does make debugging a *lot* easier if you print out the completed
						// buffer for debugging.
						buffer.append(line + "\n");
					}

					if (buffer.length() == 0) {
						// Stream was empty.  No point in parsing.
						return null;
					}
					forecastJsonStr = buffer.toString();
				} catch (IOException e) {
					Log.e("whoopsie", "Error ", e);
					// If the code didn't successfully get the weather data, there's no point in attemping
					// to parse it.
					return null;
				} finally {
					if (urlConnection != null) {
						urlConnection.disconnect();
					}
					if (reader != null) {
						try {
							reader.close();
						} catch (final IOException e) {
							Log.e("whoopsie", "Error closing stream", e);
						}
					}
				}
				return null;
			}
	}
}