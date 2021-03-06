package com.application.pglocator.ui.requests;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.application.pglocator.R;
import com.application.pglocator.constants.RequestAction;
import com.application.pglocator.constants.UserType;
import com.application.pglocator.db.DatabaseManager;
import com.application.pglocator.model.PGRequest;
import com.application.pglocator.model.PGRoom;
import com.application.pglocator.model.User;
import com.application.pglocator.util.DateUtil;
import com.application.pglocator.util.Globals;
import com.application.pglocator.viewmodel.RequestsViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;
import java.net.URL;

import pereira.agnaldo.previewimgcol.ImageCollectionView;

public class RequestDetailsFragment extends Fragment implements LifecycleOwner {

    public static final String ARG_REQUEST = "arg::Request";
    public static final String REQ_KEY = "req::Key";
    public static final String DATA_KEY = "key::Payment";

    private static final int REQUEST_CODE = 1992;
    private PGRequest request;
    private DatabaseManager databaseManager;
    private RequestsViewModel requestsViewModel;

    private ImageCollectionView imageCollectionView;
    private Button buttonAccept;
    private Button buttonReject;
    private String phone;
    private View view;
    private Button buttonPay;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getParentFragmentManager().setFragmentResultListener(REQ_KEY, this,
                (requestKey, result) -> {
                    databaseManager.acceptRejectRequests(request, RequestAction.Pay.getValue());

                    if (requestsViewModel != null) {
                        requestsViewModel.getRequest().getValue().setStatus(RequestAction.Pay.getValue());
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_request_details, container, false);
        this.view = view;

        init();

        initLayout(view);
        initListeners();
        return view;
    }

    private void init() {
        databaseManager = new DatabaseManager.Builder()
                .build();
    }

    private void initLayout(View view) {
        imageCollectionView = view.findViewById(R.id.imageCollectionView);
        TextView textViewTitle = view.findViewById(R.id.textViewTitle);
        TextView textViewDescription = view.findViewById(R.id.textViewDescription);
        TextView textViewAddress = view.findViewById(R.id.textViewAddress);
        TextView textViewRent = view.findViewById(R.id.textViewRent);

        TextView textViewName = view.findViewById(R.id.textViewName);
        TextView textViewPhone = view.findViewById(R.id.textViewPhone);
        TextView textViewRequestOn = view.findViewById(R.id.textViewRequestedOn);
        TextView textViewLocation = view.findViewById(R.id.textViewLocation);

        TextView textViewPaymentStatus = view.findViewById(R.id.textViewPaymentStatus);

        TextView textViewStatus = view.findViewById(R.id.textViewStatus);

        buttonAccept = view.findViewById(R.id.buttonAccept);
        buttonReject = view.findViewById(R.id.buttonReject);
        buttonPay = view.findViewById(R.id.buttonPay);

        requestsViewModel = new ViewModelProvider(requireActivity()).get(RequestsViewModel.class);

        requestsViewModel.getRequest().observe(getViewLifecycleOwner(), pgRequest -> {

            request = pgRequest;
            if (request == null) return;

            PGRoom pgRoom = request.getPgRoom();
            if (pgRoom != null) {
                textViewTitle.setText(pgRoom.getTitle());
                textViewDescription.setText(pgRoom.getDescription());
                textViewAddress.setText(pgRoom.getAddress());
                textViewRent.setText(String.format("Rs. %s (per month)", pgRoom.getRent()));
                textViewLocation.setText(pgRoom.getLocation());

                configureMapView(textViewLocation, pgRoom);

                User user;
                if (Globals.user.getUserType().equals(UserType.USER.getValue())) {
                    user = request.getTargetUser();
                } else {
                    user = request.getRequestedUser();
                }

                textViewName.setText(user.getDisplayName());

                String phone = user.getPhone();

                if (phone == null || phone.equals("")) {
                    textViewPhone.setText("not available");
                } else {
                    textViewPhone.setText(phone);

                    textViewPhone.setOnClickListener(v ->
                            checkCallPermission(phone)
                    );

                }

                textViewRequestOn.setText(String.format("Requested On: %s", DateUtil.getDate(request.getRequestTime())));

                if (!request.getStatus().equals(RequestAction.Pending.getValue())) {
                    buttonAccept.setVisibility(View.GONE);
                    buttonReject.setVisibility(View.GONE);
                    textViewStatus.setText(String.format("%sED", request.getStatus()));
                } else {
                    textViewStatus.setVisibility(View.GONE);
                }

                if (Globals.user.getUserType().equals(UserType.USER.getValue())) {
                    if (request.getStatus().equals(RequestAction.Accept.getValue())) {
                        buttonPay.setVisibility(View.VISIBLE);
                    }
                } else if (Globals.user.getUserType().equals(UserType.PG.getValue())) {
                    if (request.getStatus().equals(RequestAction.Accept.getValue())) {
                        textViewPaymentStatus.setText("Payment Pending");
                        textViewPaymentStatus.setVisibility(View.VISIBLE);
                    } else if (request.getStatus().equals(RequestAction.Pay.getValue())) {
                        textViewPaymentStatus.setText("Payment Complete");
                        textViewPaymentStatus.setVisibility(View.VISIBLE);
                    }
                }

                setImages(pgRoom);
            }
        });
    }

    private void configureMapView(TextView textViewLocation, PGRoom pgRoom) {
        textViewLocation.setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle("Open Map")
                        .setMessage("Show this location on Google Map")
                        .setPositiveButton("Ok", ((dialog, which) -> {
                            String uri = "http://maps.google.co.in/maps?q=" + pgRoom.getLocation();
                            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                            requireContext().startActivity(intent);
                        }))
                        .setNegativeButton("Cancel", ((dialog, which) -> {
                            dialog.dismiss();
                        }))
                        .show());
    }

    private void checkCallPermission(String phone) {

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CALL_PHONE)
                == PackageManager.PERMISSION_GRANTED) {

            makeCall(phone);
        } else {
            this.phone = phone;
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE},
                    REQUEST_CODE);
        }

    }

    private void makeCall(String phone) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Call this PG")
                .setMessage("Carrier charges will be applicable")
                .setPositiveButton("Ok", (dialog, which) -> {
                    String uri = "tel:" + phone;
                    Intent intent = new Intent(Intent.ACTION_CALL);
                    intent.setData(Uri.parse(uri));
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    dialog.dismiss();
                }).create().show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                makeCall(this.phone);
            } else {
                Snackbar.make(view, "Permission is required to make call",
                        Snackbar.LENGTH_SHORT).show();
            }
        }
    }

    private void initListeners() {
        buttonAccept.setOnClickListener(v -> {
            databaseManager.acceptRejectRequests(request, RequestAction.Accept.getValue());
            completeAction("Request Accepted");
        });
        buttonReject.setOnClickListener(v -> {
            databaseManager.acceptRejectRequests(request, RequestAction.Reject.getValue());
            completeAction("Request Rejected");
        });

        buttonPay.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
            navController.navigate(R.id.action_nav_request_details_to_paymentFragment);
        });
    }

    private void completeAction(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        NavController navController = Navigation.findNavController(requireActivity(), R.id.nav_host_fragment);
        navController.popBackStack();
    }

    private void setImages(PGRoom pgRoom) {
        for (String imgUrl : pgRoom.getImages()) {
            new ImageLoadTask().execute(imgUrl);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        Log.i("TAG", "onActivityResult: " + data.getStringExtra("addressLine"));
    }

    class ImageLoadTask extends AsyncTask<String, Integer, Bitmap> {

        @Override
        protected Bitmap doInBackground(String... imgUrl) {
            try {
                URL url = new URL(imgUrl[0]);
                return BitmapFactory.decodeStream(url.openConnection().getInputStream());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap != null) {
                imageCollectionView.addImage(bitmap);
            }
            super.onPostExecute(bitmap);
        }
    }
}
