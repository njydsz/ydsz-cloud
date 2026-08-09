import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.*;
import org.junit.platform.engine.discovery.DiscoverySelectors;

import java.io.PrintWriter;

public class Runner {
    public static void main(String[] args) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass("com.njydsz.common.core.CoreOptimizationTest"))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        PrintWriter pw = new PrintWriter(System.out);
        listener.getSummary().printTo(pw);
        listener.getSummary().printFailuresTo(pw, 20);
        pw.flush();
        long failed = listener.getSummary().getTotalFailureCount();
        System.out.println("TOTAL FAILURES = " + failed);
        if (failed > 0) System.exit(1);
    }
}
