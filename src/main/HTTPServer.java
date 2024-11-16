import javax.xml.namespace.QName;
import java.io.*;
import java.net.*;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HTTPServer {

	public static void main(String[] args) {
		// Set the port number
		int port = 6789;

		try (ServerSocket serverSocket = new ServerSocket(port)) {
			System.out.println("HTTP Server running on port " + port);

			// Infinite loop to listen for incoming connections
			while (true) {
				Socket clientSocket = serverSocket.accept();

				// Create a new thread to handle the request
				HttpRequestHandler requestHandler = new HttpRequestHandler(clientSocket);
				Thread thread = new Thread(requestHandler);
				thread.start();
			}
		} catch (IOException e) {
			System.err.println("Server exception: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

class HttpRequestHandler implements Runnable {
	private Socket socket;

	public HttpRequestHandler(Socket socket) {
		this.socket = socket;
	}

	@Override
	public void run() {
		try (InputStream is = socket.getInputStream();
			 DataOutputStream os = new DataOutputStream(socket.getOutputStream());
			 BufferedReader br = new BufferedReader(new InputStreamReader(is))) {


			StringBuilder sb = new StringBuilder();
			int c;
			while (br.ready() && (c = br.read()) != -1) {
				sb.append((char) c);
			}

			String requestLine = sb.toString();

			System.out.println("Received request: " + requestLine);

			// Now that I've received the whole request line I'm going to parse the request into an HttpRequest object.
			HttpRequest request;
			try {
				request = new HttpRequest(requestLine);
			} catch (IOException e) {
				HttpResponse response = new HttpResponse(HttpResponseCode.BAD_REQUEST, null);
				System.out.println("Response line: " + response);
				os.writeBytes(response.toString());
				throw new IOException("Bad request");
			} catch (UnsupportedOperationException e) {
				HttpResponse response = new HttpResponse(HttpResponseCode.METHOD_NOT_ALLOWED, null);
				System.out.println("Response line: " + response);
				os.writeBytes(response.toString());
				throw new IOException("Unsupported method");
			}

			HttpResponse response = handleRequest(request);
			System.out.println("Response line: " + response);

//			os.writeUTF(response.toString());
			response.write(os);
			os.flush();
		} catch (IOException e) {
			System.err.println("Request handling exception: " + e.getMessage());
			e.printStackTrace();
		} finally {
			try {
				socket.close();
			} catch (IOException e) {
				System.err.println("Socket closing exception: " + e.getMessage());
			}
		}
	}

	private HttpResponse handleRequest(HttpRequest request)  {
		HttpResponse response;
//		response = new HttpResponse(HttpResponseCode.OK, Map.of("Content-Type", "text/plain"), "Hello, world! All is good in the hood!");

		switch (request.method) {
			case GET:
				response = handleGet(request);
				break;
			case POST:
				response = handlePost(request);
				break;
			default:
				return new HttpResponse(HttpResponseCode.METHOD_NOT_ALLOWED, null, "Method not allowed");
		}

		return response;
	}

	private HttpResponse handleGet(HttpRequest request)  {
		String sanitizedUrl = sanitizeResourceUrl(request.url);


		File resource = new File(sanitizedUrl);
		if (!resource.exists()) {
			return new HttpResponse(HttpResponseCode.NOT_FOUND, null, "Resource not found. Code: 00");
		}

		byte[] data;

		try (FileInputStream fis = new FileInputStream(resource)) {
			data = fis.readAllBytes();
		} catch (IOException e) {
			return new HttpResponse(HttpResponseCode.NOT_FOUND, null, "Resource not found. Code: 01");
		}

		String contentType = contentType(sanitizedUrl);

		return new HttpResponse(HttpResponseCode.OK, Map.of("Content-Type", contentType, "Content-Length", Integer.toString(data.length)), data);
	}

	private HttpResponse handlePost(HttpRequest request)  {
		return null;
	}

	private String contentType(String file) {
		String[] split = file.split("\\.");
		String extension = split[split.length - 1];
		switch (extension) {
			case "html":
				return "text/html";
			case "jpg":
			case "jpeg":
				return "image/jpeg";
			case "png":
				return "image/png";
			case "gif":
				return "image/gif";
			case "json":
				return "application/json";
			case "tif":
				return "image/tiff";
			case "zip":
				return "application/zip";
			case "txt":
			default:
				return "text/plain";
		}
	}

	private String sanitizeResourceUrl(String url) {
		String sanitizedUrl = url.replace("\\.\\.\\/", "\\/");
		if (sanitizedUrl.startsWith("/")) {
			sanitizedUrl = sanitizedUrl.substring(1);
		}
		return sanitizedUrl;
	}
}

// This class is just constants used for sending responses back
enum HttpResponseCode {
	OK(200, "OK"),
	BAD_REQUEST(400, "BAD REQUEST"),
	NOT_FOUND(404, "NOT FOUND"),
	METHOD_NOT_ALLOWED(405, "METHOD NOT ALLOWED");

	public final int statusCode;
	public final String statusMessage;
	HttpResponseCode(int statusCode, String statusMessage) {
		this.statusCode = statusCode;
		this.statusMessage = statusMessage;
	}
}

// This class sorta kinda replicates the user-side interface for HttpResponses mirroring how I'd create a response with Springboot
class HttpResponse {

	private static final String DELIMETER = "\r\n";
	private static final String SPACE = " ";

	public final HttpResponseCode responseCode;
	public final Map<String, String> headers;
	public final byte[] body;

	HttpResponse(HttpResponseCode responseCode, Map<String, String> headers, byte[] body) {
		this.responseCode = responseCode;
		this.headers = headers;
		this.body = body;
	}

	HttpResponse(HttpResponseCode responseCode, Map<String, String> headers, String body) {
		this.responseCode = responseCode;
		this.headers = headers;
		this.body = body.getBytes(StandardCharsets.UTF_8);
	}

	HttpResponse(HttpResponseCode responseCode, Map<String, String> headers) {
		this.responseCode = responseCode;
		this.headers = headers;
		this.body = null;
	}

	public void write(DataOutputStream os) throws IOException {
		os.writeUTF(toStringWithoutBody());
		if (body != null) {
			os.write(body);
		}
	}

	private String toStringWithoutBody() {
		StringBuilder sb = new StringBuilder();
		sb.append("HTTP/1.1")
				.append(SPACE)
				.append(responseCode.statusCode)
				.append(SPACE)
				.append(responseCode.statusMessage)
				.append(SPACE)
				.append(DELIMETER);
		if (headers != null) {
			for (String key : headers.keySet()) {
				sb.append(key).append(": ").append(headers.get(key)).append(DELIMETER);
			}
		}

		sb.append(DELIMETER);
		return sb.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(toStringWithoutBody());

		if(body != null) {
			sb.append(new String(body));
		}
		return sb.toString();
	}
}

// Method constants
enum HttpMethod {

	GET("GET"), POST("POST"), UNSUPPORTED("");

	public final String methodName;

	HttpMethod(String methodName) {
		this.methodName = methodName;
	}

}

// Http Request object interprets the request, throws an IOException it if is malformed
class HttpRequest {

	private static final String HEADER_DELIMETER = ":";

	public final HttpMethod method;
	public final String url;
	public final String httpVersion;
	public final Map<String, String> headers;
	public final String body;

	HttpRequest(String requestLine) throws IOException, UnsupportedOperationException {
		BufferedReader reader = new BufferedReader(new StringReader(requestLine));
		HttpMethod method;
		String[] firstLine = reader.readLine().split(" ");

		try {
			method = HttpMethod.valueOf(firstLine[0].trim());
		} catch (IllegalArgumentException e) {
			throw new UnsupportedOperationException();
		}
		this.method = method;
		this.url = firstLine[1];
		this.httpVersion = firstLine[2];

		List<String> headers = new ArrayList<>();
		String header;
		while(reader.ready() && (header = reader.readLine()) != null && !header.isBlank()) {
			headers.add(header);
		}

		this.headers = parseHeaders(headers);

		if (!reader.ready()) {
			this.body = null;
		} else {
			this.body = reader.readLine();
		}
		reader.close();
	}

	private Map<String, String> parseHeaders(List<String> headers) throws IOException {
		Map<String, String> headerMap = new HashMap<>();
		for (String header : headers) {
			if (header.isBlank()) {
				return headerMap;
			}
			String[] pair = header.split(HEADER_DELIMETER);
			if (pair.length < 2) {
				throw new IOException("Malformed headers!");
			}
			String rest = header.substring(pair[0].length() + 1).trim();
			headerMap.put(pair[0].trim(), rest);
		}
		return headerMap;
	}
}