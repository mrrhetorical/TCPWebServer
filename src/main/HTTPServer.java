import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HTTPServer {

	public static void main(String[] args) {

		HttpsConfig config = getHttpsConfig(args);

		Thread httpThread = new Thread(() -> {
			int port = 80;
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
		});
		httpThread.start();

		if (config != null) {
			Thread httpsThread = new Thread(() -> {
				int port = 443;
				System.setProperty("javax.net.ssl.keyStore", config.path);
				System.setProperty("javax.net.ssl.keyStorePassword", config.password);
				try (SSLServerSocket serverSocket = (SSLServerSocket) SSLServerSocketFactory.getDefault().createServerSocket(port)) {
					System.out.println("HTTP Server running on port " + port);

					// Infinite loop to listen for incoming connections
					while (true) {
						SSLSocket clientSocket = (SSLSocket) serverSocket.accept();

						// Create a new thread to handle the request
						HttpRequestHandler requestHandler = new HttpRequestHandler(clientSocket);
						Thread thread = new Thread(requestHandler);
						thread.start();
					}
				} catch (IOException e) {
					System.err.println("Server exception: " + e.getMessage());
					e.printStackTrace();
				}

			});
			httpsThread.start();
		}
	}

	private static HttpsConfig getHttpsConfig(String[] args) {

		if (args.length != 2) {
			if (args.length > 0) {
				System.err.println("Starting on HTTP-only mode. Please provide both a keystore path and password!");
			}
			return null;
		}

		HttpsConfig httpsConfig = new HttpsConfig();
		httpsConfig.path = args[0];
		httpsConfig.password = args[1];
		return httpsConfig;
	}

}

class HttpsConfig {
	public String path;
	public String password;
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

			if (requestLine.isBlank()) {
				System.out.println("Empty request");
				HttpResponse response = new HttpResponse(HttpResponseCode.BAD_REQUEST, null);
				System.out.println("Response line: " + response);
				os.writeBytes(response.toString());
				os.flush();
				return;
			}

			// Now that I've received the whole request line I'm going to parse the request into an HttpRequest object.
			HttpRequest request;
			try {
				request = new HttpRequest(requestLine);
			} catch (IOException e) {
				HttpResponse response = new HttpResponse(HttpResponseCode.BAD_REQUEST, null);
				System.out.println("Response line: " + response);
				os.writeBytes(response.toString());
				throw new IOException("Bad request", e);
			} catch (UnsupportedOperationException e) {
				HttpResponse response = new HttpResponse(HttpResponseCode.METHOD_NOT_ALLOWED, null);
				System.out.println("Response line: " + response);
				os.writeBytes(response.toString());
				throw new IOException("Unsupported method", e);
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

	private HttpResponse handleRequest(HttpRequest request) {
		HttpResponse response;

		switch (request.method) {
			case GET:
				response = handleGet(request);
				break;
			default:
				return new HttpResponse(HttpResponseCode.METHOD_NOT_ALLOWED, null);
		}

		return response;
	}

	private HttpResponse handleGet(HttpRequest request) {
		String sanitizedUrl = URLDecoder.decode(request.url, StandardCharsets.UTF_8);
		Path currentPath = Paths.get("").toAbsolutePath(); //pwd
		Path resourcePath = Paths.get(currentPath.toString(), sanitizedUrl);
		System.out.printf("Resource: %s\nCurrent: %s\n", resourcePath, currentPath);
		if (!resourcePath.startsWith(currentPath)) {
			// Cheap way to check we're in a valid resource path. Using the URL decoder already handles most required sanitizations
			return new HttpResponse(HttpResponseCode.FORBIDDEN, null);
		}

		byte[] data;

		try {
			data = getFileData(resourcePath);
		} catch (FileNotFoundException e) {
			return new HttpResponse(HttpResponseCode.NOT_FOUND, null);
		} catch (IOException e) {
			return new HttpResponse(HttpResponseCode.BAD_REQUEST, null);
		}

		String contentType = contentType(sanitizedUrl);

		return new HttpResponse(HttpResponseCode.OK, Map.of("Content-Type", contentType, "Content-Length", Integer.toString(data.length)), data);
	}

	public static String contentType(String file) {
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

	public static byte[] getFileData(Path resourcePath) throws IOException {
		File resource = new File(resourcePath.toUri());
		if (!resource.exists()) {
			throw new FileNotFoundException();
		}

		byte[] data;

		try (FileInputStream fis = new FileInputStream(resource)) {
			data = fis.readAllBytes();
		}

		return data;
	}

}

// This class is just constants used for sending responses back
enum HttpResponseCode {
	OK(200, "OK", null),
	BAD_REQUEST(400, "BAD_REQUEST", Paths.get("400.html")),
	FORBIDDEN(403, "FORBIDDEN", Paths.get("403.html")),
	NOT_FOUND(404, "NOT_FOUND", Paths.get("404.html")),
	METHOD_NOT_ALLOWED(405, "METHOD_NOT_ALLOWED", Paths.get("403.html"));

	public final int statusCode;
	public final String statusMessage;
	// This is the default path of a file to be served upon no body being declared!
	public final Path defaultPath;

	HttpResponseCode(int statusCode, String statusMessage, Path defaultPath) {
		this.statusCode = statusCode;
		this.statusMessage = statusMessage;
		this.defaultPath = defaultPath;
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

		if (responseCode.defaultPath != null) {

			byte[] data;
			try {
				data = HttpRequestHandler.getFileData(responseCode.defaultPath);
			} catch (IOException ignored) {
				// this shouldn't happen so we handle gracefully
				data = null;
			}
			this.body = data;

			if (data != null) {
				String contentType = HttpRequestHandler.contentType(responseCode.defaultPath.toString());
				if (headers == null) {
					headers = new HashMap<>();
				}
				headers.putAll(Map.of("Content-Type", contentType, "Content-Length", Integer.toString(data.length)));
			}
		} else {
			this.body = null;
		}

		this.headers = headers;
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

		if (body != null) {
			sb.append(new String(body));
		}
		return sb.toString();
	}
}

// Method constants
enum HttpMethod {

	GET("GET"), UNSUPPORTED("");

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
		String first = reader.readLine();
		if (first == null) {
			throw new IOException("Bad request");
		}
		String[] firstLine = first.split(" ");

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
		while (reader.ready() && (header = reader.readLine()) != null && !header.isBlank()) {
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