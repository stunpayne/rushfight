package com.example.anim;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MatchResults extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_match_results);
		
		String res = savedInstanceState.getString("result");
		((TextView)findViewById(R.id.editText1)).setText(res);
		
	}
}
