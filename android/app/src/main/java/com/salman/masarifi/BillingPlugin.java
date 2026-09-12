package com.salman.masarifi;

import android.content.Intent;
import android.net.Uri;
import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Google Play subscription ("Pro") for the JS side.
 *
 * Deliberately thin: it reports whether Play says the user currently owns the subscription, can
 * launch the purchase flow, and can re-check on demand. The JS side owns the gating decisions and
 * caches the answer (see isPro() in www/index.html) so a feature check never has to wait on a
 * network round-trip.
 *
 * IMPORTANT for whoever builds/distributes this: Play Billing only works for a build that was
 * INSTALLED FROM GOOGLE PLAY under the same applicationId, with the subscription product created
 * in Play Console. A sideloaded APK (which is how this app is distributed today, straight from
 * GitHub Actions) gets BILLING_UNAVAILABLE here and no purchase is possible. That is not a bug in
 * this file — every method resolves with {available:false} in that case so the UI can say so
 * plainly instead of showing a dead button.
 *
 * Purchases are verified only against Play's local response, not against a server. That is enough
 * for a small paid app, but it is client-side: someone who modifies the app can bypass it. Adding
 * server-side receipt verification (Play Developer API) is the fix if that ever matters.
 */
@CapacitorPlugin(name = "Billing")
public class BillingPlugin extends Plugin {

    /** Must match the subscription's product ID created in Play Console. */
    static final String PRODUCT_ID = "pro_monthly";

    private BillingClient billingClient;
    private boolean connected = false;
    private boolean unavailable = false;
    private ProductDetails productDetails;
    /** Set when Play reports an owned, non-expired subscription. */
    private boolean isPro = false;
    /** A purchase that arrived through the listener while no call was waiting on it. */
    private PluginCall pendingPurchaseCall;

    private final PurchasesUpdatedListener purchasesListener = (billingResult, purchases) -> {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
            for (Purchase p : purchases) handlePurchase(p);
        }
        if (pendingPurchaseCall != null) {
            PluginCall call = pendingPurchaseCall;
            pendingPurchaseCall = null;
            JSObject ret = baseStatus();
            ret.put("cancelled", billingResult.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED);
            ret.put("code", billingResult.getResponseCode());
            call.resolve(ret);
        }
    };

    @Override
    public void load() {
        billingClient = BillingClient.newBuilder(getContext())
            .setListener(purchasesListener)
            .enablePendingPurchases()
            .build();
        connect(null);
    }

    /** Connects (idempotent). If a call is passed, it is resolved with the status once ready. */
    private void connect(final PluginCall callToResolve) {
        if (connected) {
            if (callToResolve != null) refreshThenResolve(callToResolve);
            return;
        }
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult result) {
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                    connected = true;
                    unavailable = false;
                    queryProduct();
                    if (callToResolve != null) refreshThenResolve(callToResolve);
                } else {
                    // BILLING_UNAVAILABLE is the expected answer on a sideloaded build or a device
                    // with no/outdated Play Store — surface it as "not available" rather than an error.
                    connected = false;
                    unavailable = true;
                    if (callToResolve != null) callToResolve.resolve(baseStatus());
                }
            }

            @Override
            public void onBillingServiceDisconnected() {
                connected = false;
            }
        });
    }

    private void queryProduct() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
            .setProductList(Collections.singletonList(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()))
            .build();
        billingClient.queryProductDetailsAsync(params, (result, list) -> {
            if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null && !list.isEmpty()) {
                productDetails = list.get(0);
            }
        });
    }

    /** Re-asks Play what the user owns, then resolves the call with the fresh status. */
    private void refreshThenResolve(final PluginCall call) {
        billingClient.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build(),
            (result, purchases) -> {
                isPro = false;
                if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                    for (Purchase p : purchases) handlePurchase(p);
                }
                call.resolve(baseStatus());
            });
    }

    private void handlePurchase(Purchase purchase) {
        if (purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return;
        if (!purchase.getProducts().contains(PRODUCT_ID)) return;
        isPro = true;
        // Play refunds an unacknowledged purchase after three days, so this is not optional.
        if (!purchase.isAcknowledged()) {
            billingClient.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build(),
                r -> {});
        }
    }

    private JSObject baseStatus() {
        JSObject ret = new JSObject();
        ret.put("available", connected && !unavailable);
        ret.put("isPro", isPro);
        ret.put("productId", PRODUCT_ID);
        // The store's own localized price string — never hardcode the number in the UI, since the
        // price is set in Play Console (and differs per country/currency).
        String price = null;
        if (productDetails != null && productDetails.getSubscriptionOfferDetails() != null
            && !productDetails.getSubscriptionOfferDetails().isEmpty()) {
            List<ProductDetails.PricingPhase> phases = productDetails.getSubscriptionOfferDetails()
                .get(0).getPricingPhases().getPricingPhaseList();
            if (!phases.isEmpty()) price = phases.get(0).getFormattedPrice();
        }
        ret.put("price", price);
        return ret;
    }

    @PluginMethod
    public void getStatus(PluginCall call) {
        if (!connected) { connect(call); return; }
        refreshThenResolve(call);
    }

    @PluginMethod
    public void purchase(PluginCall call) {
        if (!connected || productDetails == null
            || productDetails.getSubscriptionOfferDetails() == null
            || productDetails.getSubscriptionOfferDetails().isEmpty()) {
            call.resolve(baseStatus());
            return;
        }
        String offerToken = productDetails.getSubscriptionOfferDetails().get(0).getOfferToken();
        List<BillingFlowParams.ProductDetailsParams> products = new ArrayList<>();
        products.add(BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
            .setOfferToken(offerToken)
            .build());
        pendingPurchaseCall = call;
        BillingResult launch = billingClient.launchBillingFlow(getActivity(),
            BillingFlowParams.newBuilder().setProductDetailsParamsList(products).build());
        if (launch.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            pendingPurchaseCall = null;
            call.resolve(baseStatus());
        }
    }

    /** Opens the Play subscription management screen so the user can cancel or fix payment. */
    @PluginMethod
    public void openManage(PluginCall call) {
        String url = "https://play.google.com/store/account/subscriptions?sku=" + PRODUCT_ID
            + "&package=" + getContext().getPackageName();
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            getContext().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject("cannot open subscriptions page");
        }
    }
}
