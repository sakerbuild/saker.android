package com.example;

public class MainActivity extends android.app.Activity {
	@Override
	protected void onCreate(android.os.Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		androidx.drawerlayout.widget.DrawerLayout dl = findViewById(R.id.drawer_layout);
	}
}