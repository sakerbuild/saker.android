package test;

public class Main {
	//f
	public static void main(String[] args) {
		System.out.println("Main.main()");
		Runnable run = () -> {
			System.out.println("Main.main() LAMBDA ");
		};
		run.run();
	}
}