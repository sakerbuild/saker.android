package com.example;

public class MainActivity extends android.app.Activity {
	@Override
	protected void onCreate(android.os.Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		Class<?> c = androidx.multidex.MultiDex.class;
	}
}