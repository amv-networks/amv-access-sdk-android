package org.amv.access.sdk.sample;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import org.amv.access.sdk.sample.logic.CertificatesController;
import org.amv.access.sdk.sample.logic.ICertificatesController;
import org.amv.access.sdk.sample.logic.ICertificatesView;
import org.amv.access.sdk.spi.certificate.AccessCertificate;
import org.amv.access.sdk.spi.certificate.AccessCertificatePair;
import org.amv.access.sdk.spi.certificate.DeviceCertificate;
import org.amv.access.sdk.spi.error.AccessSdkException;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

public class CertificatesActivity extends Activity implements ICertificatesView {
    public static final String TAG = "AccessDemoApp";

    @BindView(R.id.title_button)
    TextView titleText;
    @BindView(R.id.refresh_button)
    ImageButton refreshButton;
    @BindView(R.id.progress_bar)
    ProgressBar progressBar;
    @BindView(R.id.progress_bar_subtext)
    TextView progressBarSubtext;

    @BindView(R.id.certificates_list_view)
    ListView listView;

    ICertificatesController controller;
    CertificatesAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);

        titleText.setText(R.string.initializing);
        setRefreshing(true);
        progressBarSubtext.setText(getString(R.string.initializing));

        controller = new CertificatesController();
        controller.initialize(this, getApplicationContext());

        refreshButton.setOnClickListener(view -> {
            setRefreshing(true);
            progressBarSubtext.setText(R.string.refreshing);
            controller.downloadCertificates();
        });

        listView.setOnItemClickListener((adapterView, view, i, l) -> {
            AccessCertificatePair certificatePair = (AccessCertificatePair) adapter.getItem(i);
            Intent intent = new Intent(CertificatesActivity.this, BroadcastActivity.class);
            intent.putExtra(BroadcastActivity.ACCESS_CERT_ID_IDENTIFIER, certificatePair.getId());
            startActivity(intent);
        });
    }

    @Override
    public void onInitializeFinished() {
        Observable.just(1)
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(foo -> progressBarSubtext.setText(R.string.load_device_certificate))
                .flatMap(foo -> controller.getDeviceCertificate())
                // add artificial delay
                .delay(250, TimeUnit.MILLISECONDS)
                .map(DeviceCertificate::getDeviceSerial)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(deviceSerial -> {
                    titleText.setText(deviceSerial.getSerialNumberHex());
                    refreshButton.setVisibility(VISIBLE);
                    refreshButton.setEnabled(false);

                    progressBarSubtext.setText(R.string.load_access_certificate);

                    controller.downloadCertificates();
                });
    }

    @Override
    public void onInitializeFailed(AccessSdkException error) {
        setRefreshing(false);

        titleText.setText(getString(R.string.initialization_error));

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.initialization_error) + ": " + error.getType().getName())
                .setMessage(error.getMessage())
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    @Override
    public void onCertificatesDownloaded(List<AccessCertificatePair> certificates) {
        setRefreshing(false);

        if (adapter == null) {
            adapter = new CertificatesAdapter(this, certificates);
            listView.setAdapter(adapter);
        } else {
            adapter.update(certificates);
        }
    }

    @Override
    public void onCertificatesDownloadFailed(AccessSdkException error) {
        setRefreshing(false);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.certificate_refresh_error) + ": " + error.getType().getName())
                .setMessage(error.getMessage())
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    @Override
    public void onCertificateRevoked(List<AccessCertificatePair> certificates) {
        onCertificatesDownloaded(certificates);
    }

    @Override
    public void onCertificateRevokeFailed(AccessSdkException error) {
        setRefreshing(false);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.certificate_revoke_error) + ": " + error.getType().getName())
                .setMessage(error.getMessage())
                .setCancelable(false)
                .setPositiveButton(getString(R.string.ok), null)
                .show();
    }

    void setRefreshing(boolean refreshing) {
        refreshButton.setEnabled(refreshing == false);
        progressBar.setVisibility(refreshing ? VISIBLE : GONE);
        progressBarSubtext.setVisibility(refreshing ? VISIBLE : View.GONE);

        listView.setVisibility(refreshing ? GONE : VISIBLE);
    }

    class CertificatesAdapter extends BaseAdapter {
        private final Context context;
        private final LayoutInflater inflater;
        private List<AccessCertificatePair> items;
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd", Locale.US);

        CertificatesAdapter(Context context, List<AccessCertificatePair> items) {
            this.context = context;
            this.items = ImmutableList.copyOf(items);
            this.inflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }

        void update(List<AccessCertificatePair> items) {
            this.items = ImmutableList.copyOf(items);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View rowView = inflater.inflate(R.layout.list_item_access_certificate, parent, false);
            final AccessCertificate cert = items.get(position).getDeviceAccessCertificate();

            TextView name = rowView.findViewById(R.id.name_text_view);
            name.setText(cert.getGainerSerial().getSerialNumberHex());

            TextView date = rowView.findViewById(R.id.date_text_view);
            String startDateTime = dateFormat.format(cert.getStartDate().getTime());
            String endDateTime = dateFormat.format(cert.getEndDate().getTime());

            String dateText = String.format("%s - %s", startDateTime, endDateTime);
            date.setText(dateText);

            if (cert.isValidNow()) {
                date.setTextColor(Color.GREEN);
            } else if (cert.isNotValidYet()) {
                date.setTextColor(Color.YELLOW);
            } else if (cert.isExpired()) {
                date.setTextColor(Color.RED);
            } else {
                date.setTextColor(Color.GRAY);
            }

            ImageButton revokeButton = rowView.findViewById(R.id.revoke_button);
            String revokeText = String.format(getString(R.string.revoke_alert_title), cert
                    .getGainerSerial().getSerialNumberHex());

            revokeButton.setOnClickListener(view -> new AlertDialog.Builder(CertificatesActivity.this)
                    .setMessage(revokeText)
                    .setCancelable(true)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.ok, (dialog, which) -> {
                        setRefreshing(true);
                        controller.revokeCertificate(items.get(position));
                    }).show());

            return rowView;
        }

        @VisibleForTesting
        List<AccessCertificatePair> getItems() {
            return ImmutableList.copyOf(this.items);
        }
    }
}
