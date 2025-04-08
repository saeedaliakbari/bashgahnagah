public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button registerJobButton = findViewById(R.id.registerJobButton);

        registerJobButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Register a one-time background job
                PeriodicWorkRequest periodicWorkRequest = new PeriodicWorkRequest.Builder(
                        MyWorker.class,
                        15, TimeUnit.MINUTES // Minimum interval is 15 minutes
                ).build();
                // Enqueue the work request
                WorkManager.getInstance(this).enqueue(periodicWorkRequest);
            }
            }
        });
    }
}