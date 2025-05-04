package perf;

class PerfMillauTest extends PerfTest {

    @Override
    String proxyImage() {
        return "codelev/millau:latest";
    }

    @Override
    String appImage() {
        return "codelev/echo-spring:latest";
    }

    @Override
    String proxyConfigFile() {
        return null;
    }

    @Override
    String proxyConfig() {
        return null;
    }
}
