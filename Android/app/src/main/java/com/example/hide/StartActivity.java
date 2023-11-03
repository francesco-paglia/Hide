package com.example.hide;

import static com.example.hide.R.id.*;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.hide.R.id;
import java.util.Objects;

public class StartActivity extends AppCompatActivity {

    // Indici per identificare i permessi richiesti:
    // 0 -> CAMERA
    // 1 -> WRITE_EXTERNAL_STORAGE
    // 2 -> READ_EXTERNAL_STORAGE
    // 3 -> RECORD_AUDIO
    private static final int NUMERO_PERMESSI = 4;                                                   // Numero di permessi richiesti
    private String permessiNome[] = new String[]                                                    // Identificativi dei permessi richiesti
            {Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO};
    private String permessiLabel[] = new String[]                                                   // Label dei permessi richiesti che verranno mostrate nell'avviso dei permessi non ancora concessi
            {"\n- Utilizzo camera", "\n- Scrittura storage esterno", "\n- Lettura storage esterno", "\n- Registrazione audio"};

    private LinearLayout permessi_l;                                                                // Layout avviso dei permessi non ancora concessi
    private TextView permessi_t;                                                                    // Testo che mostra i permessi non ancora concessi
    private Button impostazioni_b;                                                                  // Button per accedere alle impostazioni dell'app

    private LinearLayout principale_l;                                                              // Layout contenente i button per accedere alla prossima activity o uscire dall'app
    private Button inizio_b;                                                                        // Button per accedere alla prossima activity
    private Button esci_b;                                                                          // Button per uscire dall'app

    private Thread sfocaturaThread;                                                                 // Thread che si occupa di "sfocare" il logo dell'app

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_start);
        Objects.requireNonNull(getSupportActionBar()).hide();

        permessi_l = this.findViewById(LayoutPermessi);
        principale_l = this.findViewById(LayoutStart);
        permessi_t = this.findViewById(PermessiT);
        impostazioni_b = this.findViewById(PemessiImpB);
        inizio_b = this.findViewById(EntraB);
        esci_b = this.findViewById(EsciB);

        inizio_b.setOnClickListener(new View.OnClickListener() {                                    // Avvio MainActivity
            @Override
            public void onClick(View v) {
                sfocaturaThread.interrupt();                                                        // Fermo il thread della sfocatura del logo
                Intent mainActivity_intent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(mainActivity_intent);
            }
        });

        esci_b.setOnClickListener(new View.OnClickListener() {                                      // Esco e chiudo l'applicazione
            @Override
            public void onClick(View v) {
                sfocaturaThread.interrupt();
                finish();
                System.exit(0);
            }
        });

        impostazioni_b.setOnClickListener(new View.OnClickListener() {                              // Apro le impostazioni dell'app per consentire all'utente la modifica dei permessi
            @Override
            public void onClick(View v) {
                Intent imp_intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                imp_intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                Uri uri = Uri.fromParts("package", getPackageName(), null);
                imp_intent.setData(uri);
                startActivity(imp_intent);
            }
        });

        sfocaturaThread = new Thread() {                                                            // Thread che si occupa della sfocatura del logo
            @Override
            public void run() {
                Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo);                    // Immagine del logo originale
                Bitmap logoSfocato = BitmapFactory.decodeResource(getResources(), R.drawable.logo_sfocato);     // Immagine del logo sfocato
                boolean sfocato = false;                                                                        // TRUE -> Mostro logo sfocato | FALSE -> Mostro logo originale

                while(!this.isInterrupted())                                                        // Fino a quando questo thread non viene interrotto
                {
                    boolean finalSfocato = sfocato;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if(finalSfocato) {
                                ((ImageView) findViewById(LogoView)).setImageBitmap(logoSfocato);
                            } else {
                                ((ImageView) findViewById(LogoView)).setImageBitmap(logo);
                            }
                        }
                    });

                    try {
                        sfocato = !sfocato;                                                         // Cambio il tipo di logo da mostrare
                        sleep(1500);                                                           // Attendo 1,5 secondi prima di mostrare il prossimo tipo di logo
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
        sfocaturaThread.start();                                                                    // Avvio il thread
    }

    @Override
    protected void onResume() {                                                                     // Viene invocata all'avvio dell'applicazione e ogni volta che si rientra in questa schermata
        super.onResume();
        controllaPermessi();                                                                        // Controllo se i permessi da concedere sono stati effettivamente concessi
    }

    private void controllaPermessi()                                                                // Controlla che siano stati concessi tutti i permessi richiesti, prima di procedere con l'avvio dell'applicazione
    {
        boolean tuttiPermessiConcessi = true;                                                       // TRUE -> Tutti i permessi richiesti sono stati concessi | FALSE -> Dei permessi devono ancora essere concessi

        for(int indPermesso=0; indPermesso<NUMERO_PERMESSI; indPermesso++)                          // Per ogni permesso richiesto
        {
            if(ContextCompat.checkSelfPermission(this, permessiNome[indPermesso]) == PackageManager.PERMISSION_GRANTED)                          // Se il permesso sotto analisi è stato concesso
            {
                permessi_t.setText(permessi_t.getText().toString().replace(permessiLabel[indPermesso], ""));                                  // Rimuovo la label del permesso dall'avviso dei permessi non ancora concessi
            }
            else                                                                                                                                        // Se il permesso sotto analisi non è stato ancora concesso
            {
                permessi_t.setText(permessi_t.getText().toString().replace(permessiLabel[indPermesso], "") + permessiLabel[indPermesso]);     // Aggiungo la label del permesso all'avviso dei permessi non ancora concessi
                permessi_l.setVisibility(View.VISIBLE);                                                                                                  // Mostro l'avviso
                principale_l.setVisibility(View.GONE);
                tuttiPermessiConcessi = false;                                                                                                           // Indico che non tutti i permessi sono stati concessi
            }
        }

        if(tuttiPermessiConcessi)                                                                   // Se tutti i permessi richiesti sono stati concessi
        {
            permessi_l.setVisibility(View.GONE);                                                    // Nascondo l'avviso dei permessi non ancora concessi
            principale_l.setVisibility(View.VISIBLE);                                               // Mostro il layout per passare alla MainActivity o uscire dall'app
        }
    }
}