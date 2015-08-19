package com.example.anim;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements SensorEventListener {
	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private float xVel=(float) 0.8;
	private float yVel=(float) 0.8;
	private static ImageView rand[]= new ImageView[2];
	ServerSocket ss;
	DatagramSocket clientSocket;
	String OpponentIP;
	String playerIP;
	Thread ServerThread = null;
	static Boolean Transmit=false;
	InetAddress serverAddr;
	byte[] sendData;
	private static final int GamePort = 7987;
	protected static final int MSG_ID = 0x1337;
	static String mClientMsg = null;
	static RelativeLayout.LayoutParams opponentParams;
	static RelativeLayout.LayoutParams playerParams;
	static RelativeLayout.LayoutParams[] RandParams=new RelativeLayout.LayoutParams[2];
	Boolean found=false;
	JSONArray ocoords;
	JSONArray tmpcoords;
	static TextView score;
	static TextView Oscore;
	private boolean bCollided[] = new boolean[3];
	final float kFilteringFactor = 0.1f;
	static float[] accel=new float[2];
	static float[] result=new float[2];
	static int WinHeight;
	static int WinWidth;
	JSONArray RandCoords;
	static float[] SensorVals=new float[2];
	static float[] OldSensorVals=new float[2];
	static float[] OlderSensorVals=new float[2];
	static float[] OSensorVals= new float[2];
	Player player= new Player();
	Player opponent= new Player();
	Timer SendCoords;
	Timer randObj = new Timer(); 
	boolean[] visible = new boolean[3];
	int[] randX = new int[3];
	int[] randY = new int[3];
	private int lastX;
	private int lastY;
	private int count;
	private int THRESHOLD_SCORE = 10;
	Paint paint = new Paint();

	private class Player {
		private float x;
		private float y;
		private int score;
	}
	protected void onCreate(Bundle savedInstanceState) { 
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);
		mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
		mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
		RelativeLayout relative = (RelativeLayout)findViewById(R.id.layout_main);
		DrawingPanel arena = new DrawingPanel(getApplicationContext());
		relative.addView(arena); 
		count = 0;
		if(ServerThread==null)
		{
			ServerThread = new Thread(new ServerThread());
			ServerThread.start();
			AlertDialog.Builder mode = new AlertDialog.Builder(this);
			mode.setTitle("Host/Join Game");
			mode.setPositiveButton("Host", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int whichButton)	{
					new Thread(new hostThread()).start();
				}
			});
			mode.setNeutralButton("Join", new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					new Thread(new scanThread()).start();
					(new Thread(new randomListener())).start();
				}
				
			});
			mode.show();
		}
	}
	protected void onResume() {
		super.onResume();
		mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
	}

	protected void onPause() {
		super.onPause();
		mSensorManager.unregisterListener(this);
	}

	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	public void onSensorChanged(SensorEvent event) {
		if(event.sensor.getType()==Sensor.TYPE_GRAVITY)	{
			SensorVals=event.values;		
		}
	}

	class DrawingPanel extends SurfaceView implements SurfaceHolder.Callback {
		PanelThread DrawingThread;
		Bitmap playerbmp;
		Bitmap randbmp;
		Bitmap scaled;
		Bitmap background;
		public DrawingPanel(Context context) { 
			super(context);
			playerbmp=BitmapFactory.decodeResource(getResources(),R.drawable.ball);
			randbmp=BitmapFactory.decodeResource(getResources(),R.drawable.redball);
			this.setBackgroundColor(Color.TRANSPARENT);                 
			this.setZOrderOnTop(true); //necessary                
			getHolder().setFormat(PixelFormat.RGBA_8888); 
			getHolder().addCallback(this); 
		}


		@Override 
		public void onDraw(Canvas canvas) {
			OldSensorVals[1]=((float)((int)(OldSensorVals[1]*5)))/5;
			OldSensorVals[0]=((float)((int)(OldSensorVals[0]*5)))/5;
			player.x+=(OldSensorVals[1]+OlderSensorVals[1]+OSensorVals[1])/3;
			player.y+=(OldSensorVals[0]+OlderSensorVals[0]+OSensorVals[0])/3;
			player.x=Math.min(WinWidth, player.x);
			player.y=Math.min(WinHeight, player.y);
			player.x=Math.max(0, player.x);
			player.y=Math.max(0, player.y);
			if(ocoords!=null)	{
				try {
					tmpcoords=ocoords;
					opponent.x=tmpcoords.getInt(0);
					opponent.y=tmpcoords.getInt(1);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				ocoords = null;
			}
			else	{
				opponent.x += (opponent.x - lastX); 
				opponent.y += (opponent.y - lastY);
			}

			paint.setARGB(1, 0, 0, 255);
			canvas.drawBitmap(playerbmp, player.x, player.y, null);
			canvas.drawBitmap(playerbmp, opponent.x, opponent.y, null);
			canvas.drawText("" + player.score, 0, 0, paint);
			canvas.drawText("" + opponent.score, WinWidth/2, 0, paint);
			
			// Checking for collision of the player with the random object
			int dim = (int)(WinWidth/20);
			for(int i=0; i<3;i++){
				if( ((randX[i]-player.x >= -dim) && (randX[i]-player.x <= dim))  && ((randY[i]-player.y <= dim) && (randY[i]-player.y >= -dim)) && visible[i] == true)	{
					visible[i] = false;
					player.score++;		
					if(player.score==THRESHOLD_SCORE)	{
						player.score = 0;
					}
				}
				
				if( ((randX[i]-opponent.x >= -dim) && (randX[i]-opponent.x <= dim))  && ((randY[i]-opponent.y <= dim) && (randY[i]-opponent.y >= -dim)) && visible[i] == true)	{
					visible[i] = false;
					opponent.score++;		
					if(opponent.score==THRESHOLD_SCORE)	{
						opponent.score = 0;
					}
					score.setText(String.valueOf(opponent.score));
				}				
			}
			
			for(int i=0; i<3; i++)	{
				if(visible[i] == true)	{
					canvas.drawBitmap(randbmp, randX[i], randY[i], null);
				}
			}
			OSensorVals=OlderSensorVals;
			OlderSensorVals=OldSensorVals;
			OldSensorVals=SensorVals;
			lastX = (int)opponent.x;
			lastY = (int)opponent.y;
		} 

		@Override 
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) { 
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			WinHeight = getHeight();
			WinWidth = getWidth();
			playerbmp = Bitmap.createScaledBitmap(playerbmp, (int)(WinWidth/20), (int)(WinWidth/20), true);
			randbmp = Bitmap.createScaledBitmap(randbmp, (int)(WinWidth/20), (int)(WinWidth/20), true);
			WinWidth = WinWidth-(int)(WinWidth/19);
			WinHeight = WinHeight-(int)(WinWidth/19);
			setWillNotDraw(false); //Allows us to use invalidate() to call onDraw()
			DrawingThread = new PanelThread(getHolder(), this); //Start the thread that
			DrawingThread.setRunning(true);                     //will make calls to
			DrawingThread.start();                             //onDraw()
			player.x = 0; 			player.y = WinHeight/2;
			opponent.x = WinWidth; 	opponent.y = WinHeight/2;
		}


		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			try {
				DrawingThread.setRunning(false);                //Tells thread to stop
				DrawingThread.join();                           //Removes thread from mem.
			} catch (InterruptedException e) {}
		}


		class PanelThread extends Thread {
			private SurfaceHolder SHolder;
			private boolean _run = false;


			public PanelThread(SurfaceHolder surfaceHolder, DrawingPanel panel) {
				SHolder = surfaceHolder;
			}


			public void setRunning(boolean run) { //Allow us to stop the thread
				_run = run;
			}


			@Override
			public void run() {
				Canvas c;
				while (_run) {     //When setRunning(false) occurs, _run is 
					c = null;      //set to false and loop ends, stopping thread
					try {
						c = SHolder.lockCanvas(null);
						synchronized (SHolder) {
							postInvalidate();
						}
					} finally {
						if (c != null) {

							SHolder.unlockCanvasAndPost(c);
						}
					}
				}
			}
		}

	}
	class ServerThread implements Runnable {
		public void run() {
			try {
				DatagramSocket serverSocket= new DatagramSocket(GamePort);
				while (true) {
					byte[] receiveData = new byte[10];
					Message m = new Message();
					m.what = MSG_ID;
					DatagramPacket receivePacket = new DatagramPacket(receiveData,receiveData.length);
					serverSocket.receive(receivePacket);
					mClientMsg = new String(receivePacket.getData(),0,receivePacket.getLength());
					try {
						ocoords = new JSONArray(mClientMsg);
					}catch (JSONException e) {
						e.printStackTrace();
					} 
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	class randomListener implements Runnable
	{
		public void run(){
			Socket s = null;
			String st = null;
			ServerSocket rands = null;
			try {
				rands = new ServerSocket(GamePort);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			try {
				ss = new ServerSocket(GamePort);
				while (true) {
					try {
						if (s == null)
							s = rands.accept();
						BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
						st = input.readLine();
						if(st!=null)
						{
							JSONArray rand=new JSONArray(st);
							randX[count]=rand.getInt(0);
							randY[count]=rand.getInt(1);
							visible[count++]=true;
							count %= 3;
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	class hostThread implements Runnable
	{
		public void run(){
			Socket s = null;
			String st = null;
			try {
				ss = new ServerSocket(GamePort);
				while (st==null) {
					try {
						if (s == null)
							s = ss.accept();
						BufferedReader input = new BufferedReader(new InputStreamReader(s.getInputStream()));
						st = input.readLine();
						OpponentIP=s.getInetAddress().getHostAddress();
						StartClient();
						randObj.scheduleAtFixedRate(new TimerTask() {
							public void run()	{
								count %= 3;
								randX[count] = (int) (Math.random()*WinWidth);
								randY[count] = (int) (Math.random()*WinHeight);
								visible[count++] = true;
								try {
									Socket Random = new Socket(OpponentIP,GamePort);
									PrintWriter out;
									out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(Random.getOutputStream())),true);
									RandCoords = new JSONArray();
									RandCoords.put(randX[count]);
									RandCoords.put(randY[count]);
									out.println(RandCoords.toString());
									Log.d("See","this");
								} catch (Exception e) {
									// TODO Auto-generated catch block
									e.printStackTrace();
								}
							}
						}, 300, 1000);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	class scanThread implements Runnable{
		public void run() {
			String[] ip=GetIp().split("[.]");
			for(int i=1;i<255&&!found;i++)
			{
				final String Subnet=ip[0]+"."+ip[1]+"."+ip[2]+"."+i;
				new Thread(new Runnable() {

					@Override
					public void run() {
						try {
							Socket Scan = new Socket(Subnet,GamePort);
							found=true;
							PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(Scan.getOutputStream())),true);
							out.println("Connected");
							Scan.close();
							OpponentIP=Subnet;
						} catch (Exception e) {
							Log.d("Not Open",""+Subnet);
							e.printStackTrace();
						}

					}
				}).start();
			}
			try
			{
				Thread.sleep(100);
				StartClient();
			}catch(Exception e){}
		}
	}
	public void StartClient() 
	{ 
		player.x=0;		player.y=0;
		try {
			clientSocket = new DatagramSocket();
			serverAddr = InetAddress.getByName(OpponentIP);
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		Timer PacketSend=new Timer();
		PacketSend.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				JSONArray coords = new JSONArray();
				coords.put((int)player.x);
				coords.put((int)player.y);
				sendData = coords.toString().getBytes();
				try {
					DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddr, GamePort);
					clientSocket.send(sendPacket);
				} catch (UnknownHostException e) {
					Log.d("NetError", "Error1");
					e.printStackTrace();
				} catch (IOException e) {
					Log.d("NetError", "Error2");
					e.printStackTrace();
				} catch (Exception e) {
					Log.d("NetError", "Error3");
					e.printStackTrace();
				}

			}
		}, 0,10);
	}
	public String GetIp()
	{
		WifiManager wim= (WifiManager) getSystemService(WIFI_SERVICE);
		int addr=wim.getConnectionInfo().getIpAddress();
		return  ((addr & 0xFF) + "." + 
				((addr >>>= 8) & 0xFF) + "." + 
				((addr >>>= 8) & 0xFF) + "." + 
				((addr >>>= 8) & 0xFF));
	}
}
