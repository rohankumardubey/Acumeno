package org.anirudh.redquark.acumeno;

import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.util.Base64;
import com.google.api.services.language.v1beta2.CloudNaturalLanguage;
import com.google.api.services.language.v1beta2.CloudNaturalLanguageRequestInitializer;
import com.google.api.services.language.v1beta2.model.AnnotateTextRequest;
import com.google.api.services.language.v1beta2.model.AnnotateTextResponse;
import com.google.api.services.language.v1beta2.model.Document;
import com.google.api.services.language.v1beta2.model.Entity;
import com.google.api.services.language.v1beta2.model.Features;
import com.google.api.services.speech.v1beta1.Speech;
import com.google.api.services.speech.v1beta1.SpeechRequestInitializer;
import com.google.api.services.speech.v1beta1.model.RecognitionAudio;
import com.google.api.services.speech.v1beta1.model.RecognitionConfig;
import com.google.api.services.speech.v1beta1.model.SpeechRecognitionResult;
import com.google.api.services.speech.v1beta1.model.SyncRecognizeRequest;
import com.google.api.services.speech.v1beta1.model.SyncRecognizeResponse;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.anirudh.redquark.acumeno.constant.Constant.CLOUD_API_KEY;

public class MainActivity extends AppCompatActivity implements View.OnClickListener{

    private TextView textView;
    private Button browseButton;
    private Button analyzeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textView = findViewById(R.id.speech_to_text_result);
        browseButton = findViewById(R.id.browse_button);
        analyzeButton = findViewById(R.id.analyze_button);

        browseButton.setOnClickListener(this);
        analyzeButton.setOnClickListener(this);

    }

    @Override
    public void onClick(View view) {
        if (view == browseButton) {
            // Getting the file stored from the phone
            Intent filePicker = new Intent(Intent.ACTION_GET_CONTENT);
            // Restricting the file's MIME type to only FLAC - Free Loss-less Audio Codec encoding format
            filePicker.setType("*/*");
            startActivityForResult(filePicker, 1);
        } else if (view == analyzeButton) {
            analyzeSpeech();
        }
    }


    // The output of the file picker will be another Intent object containing the URI of the file the user selected.
    // To be able to access it, onActivityResult() is overridden.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == RESULT_OK) {
            // Getting the URI of the file selected
            final Uri soundUri = data.getData();

            // processing in the async task so that the main UI thread doesn't freeze.
            // The Cloud Speech API expects its audio data to be in the form of a Base64 string.
            // To generate such a string, you can read the contents of the file the user selected
            // into a byte array and pass it to the encodeBase64String() utility method offered by
            // the Google API Client library.
            AsyncTask.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        InputStream stream = null;
                        if (soundUri != null) {
                            // Here we have only URI of the file. To get the absolute path, openInputStream() method
                            // is used. This provides the access to the input stream of the file.
                            stream = getContentResolver().openInputStream(soundUri);
                        }

                        byte[] audioData = new byte[0];
                        if (stream != null) {
                            // Converting the stream to byte array
                            audioData = IOUtils.toByteArray(stream);
                        }
                        if (stream != null) {
                            stream.close();
                        }
                        String base64EncodedData = Base64.encodeBase64String(audioData);

                        //  MediaPlayer class to play the sound file. Once you point it to the URI of the file using its
                        // setDataSource() method, we must call its prepare() method to synchronously prepare the player.
                        // When the player is ready, we can call its start() method to start playing the file.
                        // Additionally, we must remember to release the player's resources once it has completed playing the file.
                        // To do so, assign an OnCompletionListener object to it and call its release() method
                        MediaPlayer player = new MediaPlayer();
                        if (soundUri != null) {
                            player.setDataSource(MainActivity.this, soundUri);
                        }
                        player.prepare();
                        player.start();

                        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            @Override
                            public void onCompletion(MediaPlayer mediaPlayer) {
                                mediaPlayer.release();
                            }
                        });

                        processSpeech(base64EncodedData);
                    } catch(IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    // Sending the Base64-encoded audio data to the Cloud Speech API to transcribe
    private void processSpeech(String data) throws IOException {
        // To be able to communicate with the Cloud Speech API
        Speech speechService = new Speech.Builder(
                AndroidHttp.newCompatibleTransport(),
                new AndroidJsonFactory(),
                null
        ).setSpeechRequestInitializer(new SpeechRequestInitializer(CLOUD_API_KEY))
                .build();

        // The Cloud Speech API must be told what language the audio file contains.
        RecognitionConfig recognitionConfig = new RecognitionConfig();
        recognitionConfig.setLanguageCode("en-US");

        // Base64-encoded string must be wrapped in a RecognitionAudio object before it can be used by the API
        RecognitionAudio recognitionAudio = new RecognitionAudio();
        recognitionAudio.setContent(data);

        // Allows us to create an HTTP request to synchronously transcribe audio data
        SyncRecognizeRequest request = new SyncRecognizeRequest();
        request.setConfig(recognitionConfig);
        request.setAudio(recognitionAudio);

        // Executing the HTTP request
        SyncRecognizeResponse response = speechService.speech().syncrecognize(request).execute();
        SpeechRecognitionResult result = response.getResults().get(0);

        final String transcript = result.getAlternatives().get(0).getTranscript();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(transcript);
            }
        });
    }

    private void analyzeSpeech(){
        final CloudNaturalLanguage naturalLanguageService = new CloudNaturalLanguage.Builder(
                AndroidHttp.newCompatibleTransport(),
                new AndroidJsonFactory(),
                null
        ).setCloudNaturalLanguageRequestInitializer(
                new CloudNaturalLanguageRequestInitializer(CLOUD_API_KEY)
        ).build();

        // Getting the transcript from the layout to analyze
        String transcript = textView.getText().toString();

        // All the text we want to analyze using the API must be placed inside a Document
        Document document = new Document();
        document.setType("PLAIN_TEXT");
        document.setLanguage("en-US");
        document.setContent(transcript);

        // Features that we want to analyze
        Features features = new Features();
        features.setExtractEntities(true);
        features.setExtractDocumentSentiment(true);

        final AnnotateTextRequest request = new AnnotateTextRequest();
        request.setDocument(document);
        request.setFeatures(features);

        // Async task so that the main UI thread doesn't freeze
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    AnnotateTextResponse response = naturalLanguageService.documents().annotateText(request).execute();
                    final List<Entity> entityList = response.getEntities();

                    // Getting the score of the sentiment
                    final float sentiment = response.getDocumentSentiment().getScore();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            StringBuilder entities = new StringBuilder();
                            for (Entity entity : entityList) {
                                entities.append("\n").append(entity.getName().toUpperCase());
                            }
                            AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("Sentiment: " + sentiment)
                                    .setMessage("This audio file talks about : " + entities)
                                    .setNeutralButton("Okay", null)
                                    .create();
                            dialog.show();
                        }
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
