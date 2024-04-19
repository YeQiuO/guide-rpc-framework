package github.javaguide.remoting.transport.netty.client;

import github.javaguide.remoting.dto.RpcResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * !优势!
 * 如果在 send 发送完数据之后对 channel 进行手动阻塞等待返回 Response，会导致Event Loop阻塞导致效率下降
 * 优化后使用 CompletableFuture 包装 Response 实现线程同步，避免了 Event Loop 阻塞
 *
 * @author shuang.kou
 * @createTime 2020年06月04日 17:30:00
 */
public class UnprocessedRequests {
    // <requireId, 用来存储响应的 resultFuture>
    private static final Map<String, CompletableFuture<RpcResponse<Object>>> UNPROCESSED_RESPONSE_FUTURES = new ConcurrentHashMap<>();

    public void put(String requestId, CompletableFuture<RpcResponse<Object>> future) {
        UNPROCESSED_RESPONSE_FUTURES.put(requestId, future);
    }

    public void complete(RpcResponse<Object> rpcResponse) {
        CompletableFuture<RpcResponse<Object>> future = UNPROCESSED_RESPONSE_FUTURES.remove(rpcResponse.getRequestId());
        if (null != future) {
            future.complete(rpcResponse);
        } else {
            throw new IllegalStateException();
        }
    }
}
