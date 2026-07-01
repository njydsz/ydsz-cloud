public class CountMain {
    public static void main(String[] args) throws Exception {
        System.out.println("=== CountMain START, pid=" + ProcessHandle.current().pid() + " ===");
        Thread.sleep(2000);
        System.out.println("=== CountMain after 2s ===");
        Thread.sleep(2000);
        System.out.println("=== CountMain after 4s ===");
        Thread.sleep(2000);
        System.out.println("=== CountMain END (6s) ===");
    }
}