

import org.apache.http.*;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import net.sf.json.JSONObject;


//模拟浏览器发送http请求完成固定地址的请求
public class HttpClientUtils {

    /**
     * 发送post请求
     * @param url
     * @param json
     * @return
     */
    public static JSONObject DO_POST(String url, JSONObject json){
        DefaultHttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(url);
        JSONObject response = null;
        try {
            StringEntity s = new StringEntity(json.toString());
            s.setContentEncoding("UTF-8");
            s.setContentType("application/json");//发送json数据需要设置contentType
            post.setEntity(s);
            HttpResponse res = client.execute(post);
            if(res.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
                HttpEntity entity = res.getEntity();
                String result = EntityUtils.toString(res.getEntity());// 返回json格式：
                response = JSONObject.fromObject(result);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    public static void main(String[] args) {
        String url="http://127.0.0.1:8080/userinfo/login";
        JSONObject params = new JSONObject();
        params.put("account", "root");
        params.put("pwd", "abc123");
        System.out.println(HttpClientUtils.DO_POST(url, params));
    }
}