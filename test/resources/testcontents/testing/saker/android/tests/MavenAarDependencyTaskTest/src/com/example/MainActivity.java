package com.example;

public class MainActivity extends androidx.appcompat.app.AppCompatActivity {
	@Override
	protected void onCreate(android.os.Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		System.out.println(R.string.app_name);
		System.out.println(R.string.search_menu_title);
		System.out.println(androidx.appcompat.R.string.search_menu_title);
	}
}