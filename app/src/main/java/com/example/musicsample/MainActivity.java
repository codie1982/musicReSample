package com.example.musicsample;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.arthenica.mobileffmpeg.Config;
import com.arthenica.mobileffmpeg.FFmpeg;

import org.jtransforms.fft.DoubleFFT_1D;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_AUDIO_REQUEST = 1;
    private static final int REQUEST_PERMISSION = 100;
    private TextView textViewStatus,textViewPeakFrequency,textViewPeakMagnitude,textViewPeakData;
    private Button buttonSelectFile;
    private CustomView customView;
    private Uri audioFileUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        textViewStatus = findViewById(R.id.textViewStatus);
        textViewPeakFrequency = findViewById(R.id.textViewPeakFrequency);
        textViewPeakMagnitude = findViewById(R.id.textViewPeakMagnitude);
        buttonSelectFile = findViewById(R.id.btnSelectedWave);
        customView = findViewById(R.id.customView);
        buttonSelectFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (checkPermission()) {
                    selectedFile();
                } else {
                    requestPermission();
                }
            }
        });
    }
    private boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_AUDIO)) {
            // Kullanıcı daha önce bu izni reddetti, bir açıklama göster.
            textViewStatus.setText("Bu uygulamanın çalışabilmesi için dosya okuma iznine ihtiyaç var.");
        }
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                selectedFile();
            } else {
                textViewStatus.setText("Dosya okuma izni reddedildi.");
            }
        }
    }
    private void selectedFile() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_AUDIO_REQUEST);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_AUDIO_REQUEST && resultCode == RESULT_OK && data != null) {
            audioFileUri = data.getData();
            textViewStatus.setText("Seçilen Dosya: " + audioFileUri.getPath());
            // Seçilen dosya üzerinde işlem yapabilirsiniz.
            new RetrieveMetadataTask().execute(audioFileUri);
        }
    }
    private class RetrieveMetadataTask extends AsyncTask<Uri, Void, String[]> {
        @Override
        protected String[] doInBackground(Uri... uris) {
            return getMetadata(uris[0]);
        }

        @Override
        protected void onPostExecute(String[] metadata) {
            if (metadata != null) {
                String sampleRate = metadata[0];
                String channels = metadata[1];
                textViewStatus.setText("Örnekleme Hızı: " + sampleRate + " Hz, Kanal Sayısı: " + channels);
                new ConvertMp3ToPcmTask().execute(audioFileUri, sampleRate, channels);
            } else {
                textViewStatus.setText("Meta veri alınamadı");
            }
        }
    }
    private String[] getMetadata(Uri uri) {

            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        mmr.setDataSource(this, uri);
        String sampleRate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE);
        String channels = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_NUM_TRACKS);
        if (channels == null) {
            // Eğer kanal sayısı alınamıyorsa, 1 (mono) veya 2 (stereo) varsayılan değerlerini kullanabilirsiniz
            String hasMonoChannel = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO);
            if (hasMonoChannel != null && hasMonoChannel.equals("yes")) {
                channels = "1"; // Varsayılan olarak mono kanal
            } else {
                channels = "2"; // Varsayılan olarak stereo kanal
            }
        }
        try {
            mmr.release();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new String[]{sampleRate, channels};
    }
    private class ConvertMp3ToPcmTask extends AsyncTask<Object, Void, String> {
        @Override
        protected String doInBackground(Object... params) {
            Uri uri = (Uri) params[0];
            String sampleRate = (String) params[1];
            String channels = (String) params[2];
            String inputPath = getRealPathFromURI(uri);
            String outputPath = getExternalFilesDir(null) + "/output.pcm";

            if (sampleRate == null) {
                sampleRate = "44100"; // Varsayılan örnekleme hızı
            }

            String[] cmd = {
                    "-y",
                    "-i", inputPath,
                    "-f", "s16le",
                    "-acodec", "pcm_s16le",
                    "-ar", sampleRate,
                    "-ac", channels,
                    outputPath
            };

            Log.d("FFmpegCommand", String.join(" ", cmd));

            Config.enableLogCallback(message -> {
                Log.d("FFmpeg", message.getText());
            });

            Config.enableStatisticsCallback(statistics -> {
                Log.d("FFmpegStatistics", statistics.toString());
            });
            int result = FFmpeg.execute(cmd);
            if (result == 0) {
                return outputPath;
            } else {
                return null;
            }
        }

        @Override
        protected void onPostExecute(String outputPath) {
            if (outputPath != null) {
                textViewStatus.setText("Dönüşüm başarılı: " + outputPath);
                new ProcessPcmDataTask().execute(outputPath);
            } else {
                textViewStatus.setText("Dönüşüm başarısız");
            }
        }
    }


    private class ProcessPcmDataTask extends AsyncTask<String, Void, List<double[]>> {
        @Override
        protected List<double[]>  doInBackground(String... paths) {
            return processPcmData(paths[0]);
        }

        @Override
        protected void onPostExecute(List<double[]> peakData) {
            textViewStatus.setText("İşlem tamamlandı");

            // Pik frekans ve büyüklük verilerini metin olarak gösterme
            /*StringBuilder peakDataText = new StringBuilder();
            for (double[] peak : peakData) {
                peakDataText.append("Frekans: ").append(peak[0]).append(" Hz, Büyüklük: ").append(peak[1]).append("\n");
            }
            textViewPeakData.setText(peakDataText.toString());*/

            if (!peakData.isEmpty()) {
                double[] peakEntry = peakData.get(0);
                textViewPeakFrequency.setText("Pik Frekans: " + peakEntry[0] + " Hz");
                textViewPeakMagnitude.setText("Pik Değeri: " + peakEntry[1]);

                // Pik verilerini CustomView'e iletme
                customView.setPeakData(peakData);
            }
        }
    }
    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = {MediaStore.Audio.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA);
            cursor.moveToFirst();
            String path = cursor.getString(column_index);
            cursor.close();
            return path;
        }
        return null;
    }

    private List<double[]> processPcmData(String pcmPath) {
        int chunkSize = 4096; // Daha küçük parçalara bölmek için chunk boyutu
        List<double[]> peakEntries = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(pcmPath)) {
            byte[] buffer = new byte[chunkSize * 2]; // 16 bit (2 byte) mono PCM
            double[] samples = new double[chunkSize];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                // PCM verilerini double dizisine çevir
                for (int i = 0; i < bytesRead / 2; i++) {
                    samples[i] = ByteBuffer.wrap(buffer, i * 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                }
                // FFT işlemi
                DoubleFFT_1D fft = new DoubleFFT_1D(chunkSize);
                double[] fftData = new double[chunkSize * 2];
                System.arraycopy(samples, 0, fftData, 0, chunkSize);
                fft.realForwardFull(fftData);

                // Peak frekans ve magnitude hesapla
                double[] result = findPeakFrequency(fftData, 44100);
                peakEntries.add(result);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return peakEntries;
    }
    private double[] findPeakFrequency(double[] fftData, double sampleRate) {
        double[] magnitude = new double[fftData.length / 2];
        for (int i = 0; i < magnitude.length; i++) {
            double real = fftData[2 * i];
            double imag = fftData[2 * i + 1];
            magnitude[i] = Math.sqrt(real * real + imag * imag);
        }
        double peakValue = 0;
        int peakIndex = 0;
        for (int i = 0; i < magnitude.length; i++) {
            if (magnitude[i] > peakValue) {
                peakValue = magnitude[i];
                peakIndex = i;
            }
        }
        double peakFrequency = (double) peakIndex * sampleRate / (magnitude.length * 2);
        return new double[]{peakFrequency, peakValue};
    }
}