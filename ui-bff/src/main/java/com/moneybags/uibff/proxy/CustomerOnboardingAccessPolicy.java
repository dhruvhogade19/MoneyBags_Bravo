package com.moneybags.uibff.proxy;

import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;

/**
 * The small set of gateway APIs a customer may use before KYC is approved.
 * Upstream services still enforce ownership and OAuth scopes for every request.
 */
final class CustomerOnboardingAccessPolicy {
    private static final Pattern CIF_PROFILE = Pattern.compile("^/api/v1/cifs/[0-9]+$");
    private static final Pattern KYC_READ = Pattern.compile(
            "^/api/v1/kycs(?:/[0-9]+(?:/documents(?:/[0-9]+)?)?)?$");
    private static final Pattern KYC_UPLOAD = Pattern.compile(
            "^/api/v1/kycs/[0-9]+/documents$");
    private static final Pattern NOTIFICATION_READ = Pattern.compile(
            "^/api/notifications(?:/[0-9]+)?$");
    private static final Pattern PRODUCT_READ = Pattern.compile(
            "^/api/(?:v1/)?products(?:/.*)?$");
    private static final Pattern BENCHMARK_READ = Pattern.compile(
            "^/api/benchmarks(?:/.*)?$");

    private CustomerOnboardingAccessPolicy() {
    }

    static boolean isAllowed(HttpMethod method, String path) {
        if (HttpMethod.POST.equals(method) && "/api/v1/cifs".equals(path)) return true;
        if ((HttpMethod.GET.equals(method) || HttpMethod.PUT.equals(method))
                && CIF_PROFILE.matcher(path).matches()) return true;
        if (HttpMethod.GET.equals(method) && KYC_READ.matcher(path).matches()) return true;
        if (HttpMethod.POST.equals(method) && KYC_UPLOAD.matcher(path).matches()) return true;
        if (HttpMethod.GET.equals(method) && NOTIFICATION_READ.matcher(path).matches()) return true;
        return HttpMethod.GET.equals(method)
                && (PRODUCT_READ.matcher(path).matches() || BENCHMARK_READ.matcher(path).matches());
    }
}
