package lvc.cds;

public class App {

    public static void countWords(String fName) {
        System.out.println("Hi: " + fName);
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
        }
        System.out.println("Bye: " + fName);
    }

    public static void main(String[] args) throws InterruptedException {
        final ActiveObject ao = new ActiveObject();

        var f1 = ao.enqueue(() -> countWords("task 1"));
        final var f2 = ao.enqueue(() -> countWords("task 2"));
        var f3 = f1.then((v) -> {
            countWords("task 3");
            System.out.println("done waiting for f2");
            return null;
        });

        var f4 = f3.then((v) -> {
            System.out.println("hi from continuation A");
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
            return 42;
        }).then( (n) -> {
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
            }
            System.out.println("hi from continuation B + " + n);
            var f5 = ao.enqueue(() -> countWords("task 5"));
            System.out.println("waiting for f5");
            f5.get();
            return "bye";
        });
        System.out.println("main");
        System.out.println(f4.get());


        ao.terminate();
    }
}
