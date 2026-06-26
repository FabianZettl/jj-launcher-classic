package com.themoon.y1;

import android.content.Context;
import android.net.wifi.WifiManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.util.Locale;

public class Y1WebServer extends Thread {
    private ServerSocket serverSocket;
    private boolean running = true;
    private File rootFolder;
    private Context context;

    public Y1WebServer(Context context, File originalRootFolder) {
        this.context = context;
        this.rootFolder = new File("/storage/sdcard0"); // 🚀 기기 전체 루트 폴더로 고정
    }

    public void run() {
        try {
            serverSocket = new ServerSocket(8080);
            while (running) {
                Socket socket = serverSocket.accept();
                new Thread(new RequestHandler(socket)).start();
            }
        } catch (Exception e) {}
    }

    public void stopServer() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch(Exception e){}
    }

    public String getLocalIpAddress() {
        try {
            WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            int ipAddress = wm.getConnectionInfo().getIpAddress();
            return String.format(Locale.US, "%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        } catch (Exception ex) { return "Unknown IP"; }
    }

    private void deleteFileOrFolder(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteFileOrFolder(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    private class RequestHandler implements Runnable {
        private Socket socket;
        public RequestHandler(Socket socket) { this.socket = socket; }

        private String readHeaderLine(InputStream is) throws java.io.IOException {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = is.read()) != -1) {
                if (c == '\r') continue;
                if (c == '\n') break;
                sb.append((char) c);
            }
            return sb.toString();
        }

        public void run() {
            try {
                InputStream is = socket.getInputStream();
                OutputStream os = socket.getOutputStream();

                String requestLine = readHeaderLine(is);
                if (requestLine == null || requestLine.isEmpty()) return;

                String[] parts = requestLine.split(" ");
                String method = parts[0];
                String path = parts[1];

                int contentLength = 0;
                String line;
                while (!(line = readHeaderLine(is)).isEmpty()) {
                    if (line.toLowerCase().startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.split(":")[1].trim());
                    }
                }

                // 1️⃣ 화면 UI 전송 (프론트엔드 - 인라인 플레이어 + 🚀 텍스트 에디터 탑재 + 🚀 드래그 앤 드롭 지원)
                if (method.equals("GET") && path.equals("/")) {
                    String html = "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'>" +
                            "<title>Y1 File Manager</title><style>" +
                            "body{font-family:sans-serif; background:#111; color:#eee; padding:20px; text-align:center; max-width:800px; margin:0 auto; padding-bottom:120px;} " +
                            "input, select, button{font-size:14px; padding:10px; margin:5px; border-radius:5px; border:none; outline:none;} " +
                            "input[type=text]{width:calc(100% - 100px); background:#333; color:#fff;} " +
                            "button{background:#00ffff; color:#000; font-weight:bold; cursor:pointer;} " +
                            "button.danger{background:#ff4444; color:#fff; padding:8px 12px;} " +
                            "button.action{background:#44ff44; color:#000; padding:8px 12px;} " +
                            ".box{background:#222; padding:15px; border-radius:10px; margin:15px 0; text-align:left;} " +
                            ".item{display:flex; justify-content:space-between; align-items:center; padding:10px; border-bottom:1px solid #444; cursor:pointer; transition:0.2s;} " +
                            ".item:hover{background:#333;} " +
                            ".item-left{display:flex; align-items:center; flex-grow:1; overflow:hidden; gap:10px;} " +
                            ".item-name{white-space:nowrap; overflow:hidden; text-overflow:ellipsis;} " +
                            ".thumb{width:40px; height:40px; object-fit:cover; border-radius:5px; background:#000;} " +
                            ".icon{font-size:24px; width:40px; text-align:center;} " +
                            ".btn-group{display:flex; gap:5px;} " +
                            "#audioBox{position:fixed; bottom:0; left:0; right:0; background:#222; border-top:2px solid #00ffff; padding:15px; display:none; z-index:100;} " +
                            "audio{width:100%; max-width:800px; margin:0 auto; display:block; outline:none;} " +

                            // 🚀 [추가] 드래그 앤 드롭 시 박스 테두리와 배경색이 예쁘게 변하는 애니메이션
                            "#uploadBox{transition:0.3s; border:2px solid transparent;} " +
                            "#uploadBox.dragover{background:#333; border:2px dashed #00ffff;} " +

                            "#editorBox{position:fixed; top:0; left:0; width:100%; height:100%; background:#111; z-index:200; padding:20px; box-sizing:border-box; display:none;} " +
                            "#editorArea{width:100%; height:calc(100% - 120px); background:#222; color:#44ff44; font-family:monospace; font-size:14px; border:1px solid #444; padding:10px; resize:none;} " +
                            "#editorTitle{color:#00ffff; margin-top:0; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;}" +
                            "</style></head><body>" +
                            "<h2>📁 Y1 Wireless File Manager</h2>" +

                            // 업로드/폴더생성 박스 (🚀 ID 부여 및 안내 문구 추가)
                            "<div class='box' id='uploadBox'>" +
                            "<div style='font-size:18px; margin-bottom:10px;'>📍 <span id='currentPathText'>/</span></div>" +
                            "<div style='text-align:center; color:#00ffff; font-size:13px; margin-bottom:15px; border-bottom:1px solid #444; padding-bottom:10px;'>💡 <b>Drag & Drop</b> files anywhere in this box to upload instantly!</div>" +
                            "<div style='display:flex; gap:5px; margin-bottom:10px;'>" +
                            "<input type='text' id='fName' placeholder='New folder name...'>" +
                            "<button onclick='createFolder()'>Create</button></div>" +
                            "<div style='display:flex; gap:5px; align-items:center;'>" +
                            "<input type='file' id='fInput' multiple accept='*/*' style='flex-grow:1;'>" +
                            "<button onclick='uploadAll()' class='action'>Upload Here</button></div>" +
                            "<div id='status' style='margin-top:10px; color:#4f4; font-weight:bold;'></div>" +
                            "</div>" +

                            // 파일 리스트 박스
                            "<div class='box' id='fileList'>Loading...</div>" +

                            // 플로팅 오디오 플레이어
                            "<div id='audioBox'>" +
                            "<div id='audioTitle' style='max-width:800px; margin:0 auto 10px; font-weight:bold; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; color:#00ffff;'></div>" +
                            "<audio id='audioPlayer' controls controlsList='nodownload'></audio>" +
                            "</div>" +

                            // 전체화면 텍스트 에디터
                            "<div id='editorBox'>" +
                            "<h3 id='editorTitle'>📝 Edit File</h3>" +
                            "<textarea id='editorArea' spellcheck='false' wrap='off'></textarea>" +
                            "<div style='display:flex; gap:10px; margin-top:10px;'>" +
                            "<button class='action' style='flex:1;' onclick='saveFile()'>💾 Save Settings</button>" +
                            "<button class='danger' style='flex:1;' onclick='closeEditor()'>Cancel</button>" +
                            "</div></div>" +

                            "<script>" +
                            "let currentPath = '';" +
                            "function loadList() {" +
                            "  fetch('/api/list?dir=' + encodeURIComponent(currentPath)).then(r=>r.json()).then(data => {" +
                            "    document.getElementById('currentPathText').innerText = '/' + currentPath;" +
                            "    let html = '';" +
                            "    if(currentPath !== '') html += `<div class='item' onclick='goUp()'><div class='item-left'><div class='icon'>🔙</div><b>[Go Back]</b></div></div>`;" +
                            "    data.forEach(f => {" +
                            "      let ext = f.name.split('.').pop().toLowerCase();" +
                            "      let isImg = ['jpg','jpeg','png','webp','gif'].includes(ext);" +
                            "      let isAudio = ['mp3','flac','wav','ogg','m4a','aac'].includes(ext);" +
                            "      let isText = ['json','txt','xml','ini','md','m3u','m3u8','eq'].includes(ext);" +
                            "      let fullPath = currentPath ? currentPath + '/' + f.name : f.name;" +
                            "      let safePath = encodeURIComponent(fullPath);" +

                            "      let iconHtml = f.isDir ? `<div class='icon'>📁</div>` : " +
                            "                     isImg ? `<img src='/api/file?path=${safePath}' class='thumb' loading='lazy'>` : " +
                            "                     isAudio ? `<div class='icon'>🎵</div>` : " +
                            "                     isText ? `<div class='icon'>📝</div>` : `<div class='icon'>📄</div>`;" +

                            "      let rowAction = f.isDir ? `onclick=\"goInto('${f.name.replace(/'/g, \"\\\\'\")}')\"` : " +
                            "                      isAudio ? `onclick=\"playAudio('${safePath}', '${f.name.replace(/'/g, \"\\\\'\")}')\"` : " +
                            "                      isText ? `onclick=\"openEditor(event, '${safePath}', '${f.name.replace(/'/g, \"\\\\'\")}')\"` : " +
                            "                      isImg ? `onclick=\"window.open('/api/file?path=${safePath}', '_blank')\"` : '';" +

                            "      let editBtn = isText ? `<button class='action' style='background:#ffa500;' onclick=\"openEditor(event, '${safePath}', '${f.name.replace(/'/g, \"\\\\'\")}')\">Edit</button>` : '';" +
                            "      let renameBtn = `<button class='action' style='background:#2196F3;' onclick=\"renameItem(event, '${f.name.replace(/'/g, \"\\\\'\")}')\">Rename</button>`;" +

                            "      html += `<div class='item' ${rowAction}>` +" +
                            "              `<div class='item-left'>${iconHtml}<span class='item-name'>${f.name}</span></div>` +" +
                            "              `<div class='btn-group'>${editBtn}${renameBtn}<button class='danger' onclick=\"deleteItem(event, '${f.name.replace(/'/g, \"\\\\'\")}')\">Delete</button></div>` +" +
                            "              `</div>`;" +
                            "    });" +
                            "    if(data.length===0 && currentPath === '') html += '<div style=\"padding:10px;\">No files found.</div>';" +
                            "    document.getElementById('fileList').innerHTML = html;" +
                            "  });" +
                            "}" +
                            "function goInto(dirName) { currentPath = currentPath ? currentPath + '/' + dirName : dirName; loadList(); }" +
                            "function goUp() { let parts = currentPath.split('/'); parts.pop(); currentPath = parts.join('/'); loadList(); }" +

                            "function playAudio(path, name) {" +
                            "  document.getElementById('audioBox').style.display = 'block';" +
                            "  document.getElementById('audioTitle').innerText = '▶ ' + name;" +
                            "  let player = document.getElementById('audioPlayer');" +
                            "  player.src = '/api/file?path=' + path;" +
                            "  player.play();" +
                            "}" +

                            "let editingPath = '';" +
                            "function openEditor(e, path, name) {" +
                            "  if(e) e.stopPropagation();" +
                            "  editingPath = path;" +
                            "  document.getElementById('editorTitle').innerText = '📝 Editing: ' + name;" +
                            "  document.getElementById('editorArea').value = 'Loading content...';" +
                            "  document.getElementById('editorBox').style.display = 'block';" +
                            "  fetch('/api/file?path=' + path).then(r => r.text()).then(txt => {" +
                            "    document.getElementById('editorArea').value = txt;" +
                            "  });" +
                            "}" +
                            "function closeEditor() { document.getElementById('editorBox').style.display = 'none'; editingPath=''; }" +
                            "function saveFile() {" +
                            "  let content = document.getElementById('editorArea').value;" +
                            "  fetch('/api/save?path=' + editingPath, { method: 'POST', body: content }).then(() => {" +
                            "    alert('✅ File saved successfully!'); closeEditor();" +
                            "  }).catch(e => alert('Failed to save.'));" +
                            "}" +

                            "function createFolder() { " +
                            "  var n = document.getElementById('fName').value; if(!n) return;" +
                            "  fetch('/api/create?dir=' + encodeURIComponent(currentPath) + '&name=' + encodeURIComponent(n), {method:'POST'}).then(()=>{ document.getElementById('fName').value=''; loadList();});" +
                            "}" +
                            "function renameItem(e, oldName) { " +
                            "  e.stopPropagation();" +
                            "  var newName = prompt('Enter new name for: ' + oldName, oldName);" +
                            "  if(!newName || newName === oldName) return;" +
                            "  fetch('/api/rename?dir=' + encodeURIComponent(currentPath) + '&old=' + encodeURIComponent(oldName) + '&new=' + encodeURIComponent(newName), {method:'POST'}).then(()=>loadList());" +
                            "}" +
                            "function deleteItem(e, name) { " +
                            "  e.stopPropagation();" +
                            "  if(!confirm('Delete ' + name + '?')) return;" +
                            "  fetch('/api/delete?path=' + encodeURIComponent(currentPath ? currentPath + '/' + name : name), {method:'POST'}).then(()=>loadList());" +
                            "}" +

                            // 🚀 [핵심 개조] 파라미터로 파일을 던져주면 그 파일들을 업로드하고, 안 던져주면 기존 버튼 방식을 씁니다.
                            "async function uploadAll(droppedFiles) { " +
                            "  var files = droppedFiles || document.getElementById('fInput').files; var st = document.getElementById('status'); " +
                            "  if(files.length === 0) return;" +
                            "  for(var i=0; i<files.length; i++) { " +
                            "    st.innerText = 'Uploading: ' + files[i].name + ' (' + (i+1) + '/' + files.length + ')'; " +
                            "    await fetch('/api/upload?dir=' + encodeURIComponent(currentPath) + '&name=' + encodeURIComponent(files[i].name), {method:'POST', body:files[i]}); " +
                            "  } " +
                            "  st.innerText = '✅ Upload Complete!'; document.getElementById('fInput').value=''; loadList();" +
                            "}" +

                            // 🚀 [폴더 지원 엔진] 드래그한 폴더 내부를 스캔하여 구조를 파악하는 업그레이드 함수
                            "async function uploadFolderItems(fileList) { " +
                            "  var st = document.getElementById('status'); " +
                            "  for(var i=0; i<fileList.length; i++) { " +
                            "    let item = fileList[i]; " +
                            "    let displayPath = item.path ? item.path + item.file.name : item.file.name; " +
                            "    st.innerText = 'Uploading: ' + displayPath + ' (' + (i+1) + '/' + fileList.length + ')'; " +
                            "    let targetDir = currentPath; " +
                            "    if(item.path) { " +
                            "       let subDir = item.path.replace(/\\/$/, ''); " + // 끝에 붙은 슬래시 제거
                            "       targetDir = currentPath ? currentPath + '/' + subDir : subDir; " +
                            "    } " +
                            "    await fetch('/api/upload?dir=' + encodeURIComponent(targetDir) + '&name=' + encodeURIComponent(item.file.name), {method:'POST', body:item.file}); " +
                            "  } " +
                            "  st.innerText = '✅ Folder Upload Complete!'; loadList();" +
                            "}" +

                            // 🚀 [드래그 앤 드롭 전용 이벤트 리스너] 폴더와 파일을 완벽하게 구분해서 스캔합니다!
                            "let dropZone = document.getElementById('uploadBox');" +
                            "dropZone.addEventListener('dragover', function(e) { e.preventDefault(); dropZone.classList.add('dragover'); });" +
                            "dropZone.addEventListener('dragleave', function(e) { e.preventDefault(); dropZone.classList.remove('dragover'); });" +
                            "dropZone.addEventListener('drop', function(e) { " +
                            "  e.preventDefault(); dropZone.classList.remove('dragover'); " +
                            "  let items = e.dataTransfer.items; " +
                            "  if(!items) { if(e.dataTransfer.files.length > 0) uploadAll(e.dataTransfer.files); return; } " + // 구형 브라우저 방어막
                            "  " +
                            "  document.getElementById('status').innerText = 'Scanning dropped items...'; " +
                            "  let filesToUpload = []; " +
                            "  let pending = 0; " +
                            "  " +
                            "  function scanEntry(item, path) { " +
                            "    if(item.isFile) { " +
                            "      pending++; " +
                            "      item.file(f => { filesToUpload.push({file: f, path: path}); pending--; checkDone(); }); " +
                            "    } else if(item.isDirectory) { " +
                            "      let dirReader = item.createReader(); " +
                            "      pending++; " +
                            "      dirReader.readEntries(entries => { " +
                            "        entries.forEach(entry => scanEntry(entry, path + item.name + '/')); " +
                            "        pending--; checkDone(); " +
                            "      }); " +
                            "    } " +
                            "  } " +
                            "  function checkDone() { " +
                            "    if(pending === 0) { " +
                            "      if(filesToUpload.length > 0) uploadFolderItems(filesToUpload); " +
                            "      else document.getElementById('status').innerText = 'No files found.'; " +
                            "    } " +
                            "  } " +
                            "  for(let i=0; i<items.length; i++) { " +
                            "    let entry = items[i].webkitGetAsEntry(); " +
                            "    if(entry) scanEntry(entry, ''); " +
                            "  } " +
                            "});" +

                            "window.onload = loadList;" +
                            "</script></body></html>";

                    os.write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\n\r\n" + html).getBytes("UTF-8"));
                }
                // 2️⃣ [API] 파일 및 폴더 리스트 응답 (JSON 형식)
                else if (method.equals("GET") && path.startsWith("/api/list")) {
                    String q = path.contains("?") ? path.split("\\?")[1] : "";
                    String dirStr = "";
                    if (q.startsWith("dir=")) dirStr = URLDecoder.decode(q.substring(4), "UTF-8");

                    File targetDir = dirStr.isEmpty() ? rootFolder : new File(rootFolder, dirStr);
                    StringBuilder json = new StringBuilder("[");

                    if (targetDir.exists() && targetDir.isDirectory()) {
                        File[] files = targetDir.listFiles();
                        if (files != null) {
                            for (int i=0; i<2; i++) {
                                for (File f : files) {
                                    boolean isDir = f.isDirectory();
                                    if ((i == 0 && isDir) || (i == 1 && !isDir)) {
                                        if (json.length() > 1) json.append(",");
                                        json.append("{\"name\":\"").append(f.getName().replace("\"", "\\\"")).append("\",\"isDir\":").append(isDir).append("}");
                                    }
                                }
                            }
                        }
                    }
                    json.append("]");
                    os.write(("HTTP/1.1 200 OK\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + json.toString()).getBytes("UTF-8"));
                }

                // 3️⃣ [API] 폴더 생성
                else if (method.equals("POST") && path.startsWith("/api/create")) {
                    String q = path.split("\\?")[1];
                    String[] params = q.split("&");
                    String dirStr = "", name = "";
                    for (String p : params) {
                        if (p.startsWith("dir=")) dirStr = URLDecoder.decode(p.substring(4), "UTF-8");
                        if (p.startsWith("name=")) name = URLDecoder.decode(p.substring(5), "UTF-8");
                    }
                    File targetDir = dirStr.isEmpty() ? rootFolder : new File(rootFolder, dirStr);
                    File newDir = new File(targetDir, name);
                    newDir.mkdirs();
                    os.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes("UTF-8"));
                }

                // 4️⃣ [API] 파일 및 폴더 삭제
                else if (method.equals("POST") && path.startsWith("/api/delete")) {
                    String q = path.split("\\?")[1];
                    String targetPath = URLDecoder.decode(q.substring(5), "UTF-8");
                    File targetFile = new File(rootFolder, targetPath);
                    if (targetFile.exists()) {
                        deleteFileOrFolder(targetFile);
                    }
                    os.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes("UTF-8"));
                }

                // 🚀 [수정 5] [API] 파일 및 폴더 이름 변경 (Rename) 엔진 장착!
                else if (method.equals("POST") && path.startsWith("/api/rename")) {
                    String q = path.split("\\?")[1];
                    String[] params = q.split("&");
                    String dirStr = "", oldName = "", newName = "";
                    for (String p : params) {
                        if (p.startsWith("dir=")) dirStr = URLDecoder.decode(p.substring(4), "UTF-8");
                        if (p.startsWith("old=")) oldName = URLDecoder.decode(p.substring(4), "UTF-8");
                        if (p.startsWith("new=")) newName = URLDecoder.decode(p.substring(4), "UTF-8");
                    }

                    File targetDir = dirStr.isEmpty() ? rootFolder : new File(rootFolder, dirStr);
                    File oldFile = new File(targetDir, oldName);
                    File newFile = new File(targetDir, newName);

                    // 기존 파일이 존재하고 새 이름의 파일이 없을 때만 안전하게 이름 변경 실행
                    if (oldFile.exists() && !newFile.exists()) {
                        oldFile.renameTo(newFile);
                    }
                    os.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes("UTF-8"));
                }

                // 5️⃣ [API] 파일 읽기 (스트리밍, 다운로드, 코드 불러오기)

                // 5️⃣ [API] 파일 읽기 (스트리밍, 다운로드, 코드 불러오기)
                else if (method.equals("GET") && path.startsWith("/api/file")) {
                    String q = path.split("\\?")[1];
                    String targetPath = URLDecoder.decode(q.substring(5), "UTF-8");
                    File targetFile = new File(rootFolder, targetPath);

                    if (!targetFile.exists() || targetFile.isDirectory()) {
                        os.write("HTTP/1.1 404 Not Found\r\n\r\nNot Found".getBytes("UTF-8"));
                    } else {
                        String mimeType = "application/octet-stream";
                        String lowerName = targetFile.getName().toLowerCase();
                        if (lowerName.endsWith(".mp3")) mimeType = "audio/mpeg";
                        else if (lowerName.endsWith(".flac")) mimeType = "audio/flac";
                        else if (lowerName.endsWith(".wav")) mimeType = "audio/wav";
                        else if (lowerName.endsWith(".ogg")) mimeType = "audio/ogg";
                        else if (lowerName.endsWith(".m4a") || lowerName.endsWith(".aac")) mimeType = "audio/mp4";
                        else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) mimeType = "image/jpeg";
                        else if (lowerName.endsWith(".png")) mimeType = "image/png";
                        else if (lowerName.endsWith(".json")) mimeType = "application/json";
                            // 🚀 [수정 2] 방금 추가한 파일들도 브라우저가 순수한 '글자'로 인식해서 에디터 창에 띄우도록 명시!
                        else if (lowerName.endsWith(".txt") || lowerName.endsWith(".m3u") || lowerName.endsWith(".m3u8") || lowerName.endsWith(".eq")) mimeType = "text/plain";

                        String header = "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + targetFile.length() + "\r\n" +
                                "Accept-Ranges: bytes\r\n\r\n";
                        os.write(header.getBytes("UTF-8"));

                        FileInputStream fis = new FileInputStream(targetFile);
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = fis.read(buffer)) != -1) {
                            os.write(buffer, 0, bytesRead);
                        }
                        fis.close();
                    }
                }

                // 6️⃣ [API] 파일 업로드
                else if (method.equals("POST") && path.startsWith("/api/upload")) {
                    String q = path.split("\\?")[1];
                    String[] params = q.split("&");
                    String dirStr = "", name = "unnamed.file";
                    for (String p : params) {
                        if (p.startsWith("dir=")) dirStr = URLDecoder.decode(p.substring(4), "UTF-8");
                        if (p.startsWith("name=")) name = URLDecoder.decode(p.substring(5), "UTF-8");
                    }

                    File targetDir = dirStr.isEmpty() ? rootFolder : new File(rootFolder, dirStr);
                    if (!targetDir.exists()) targetDir.mkdirs();
                    File outFile = new File(targetDir, name);

                    FileOutputStream fos = new FileOutputStream(outFile);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    int totalRead = 0;
                    while (totalRead < contentLength && (bytesRead = is.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                    fos.flush();
                    try { fos.getFD().sync(); } catch(Exception e){}
                    fos.close();

                    os.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes("UTF-8"));
                }

                // 7️⃣ [API] 🚀 텍스트 파일 저장 (코드 에디터에서 전송된 텍스트를 기기에 덮어쓰기)
                else if (method.equals("POST") && path.startsWith("/api/save")) {
                    String q = path.split("\\?")[1];
                    String targetPath = URLDecoder.decode(q.substring(5), "UTF-8");
                    File targetFile = new File(rootFolder, targetPath);

                    FileOutputStream fos = new FileOutputStream(targetFile);
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    int totalRead = 0;
                    // 전달받은 텍스트 몸통(Body)을 파일로 그대로 쭉 밀어 넣습니다.
                    while (totalRead < contentLength && (bytesRead = is.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead))) != -1) {
                        fos.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                    }
                    fos.flush();
                    try { fos.getFD().sync(); } catch(Exception e){}
                    fos.close();

                    os.write("HTTP/1.1 200 OK\r\n\r\nOK".getBytes("UTF-8"));
                }

                os.flush();
            } catch (Exception e) {}
            finally {
                try { socket.close(); } catch (Exception e) {}
            }
        }
    }
}