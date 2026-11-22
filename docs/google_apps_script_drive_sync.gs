/**
 * Bhoomgaadi Procurement v2.0 — Permanent Google Drive Auto-Sync Script
 *
 * ONE-TIME SETUP (2 minutes):
 * 1. Go to https://script.google.com and click "New project".
 * 2. Delete the default code and paste this entire script.
 * 3. Replace "CHANGE_TO_YOUR_SECRET_KEY" below with your own secret passphrase
 *    (at least 8 characters, e.g., Bhoomgaadi2026#Sync).
 * 4. Optional: Set DEFAULT_ROOT_FOLDER_ID to your Google Drive folder ID
 *    (the ID after /folders/ in the Drive URL), or leave it blank to auto-create
 *    a folder named "Bhoomgaadi_Procurement_Sync" in your Drive.
 * 5. Click "Deploy" -> "New deployment" -> Type: "Web app":
 *      - Execute as: Me (your Gmail)
 *      - Who has access: Anyone
 * 6. Click "Deploy", authorize Drive permission, and copy the Web App URL (ends in /exec).
 * 7. Paste the Web App URL and your Secret Key into the Bhoomgaadi app:
 *    Settings -> General -> Google Drive Sync Settings -> Test Connection -> Save.
 */

const SYNC_SECRET = "CHANGE_TO_YOUR_SECRET_KEY";
const DEFAULT_ROOT_FOLDER_ID = ""; // Optional: paste Drive Folder ID or leave empty
const DEFAULT_ROOT_FOLDER_NAME = "Bhoomgaadi_Procurement_Sync";
const ALLOWED_MIME_TYPES = [
  "application/pdf",
  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
  "application/octet-stream",
  "text/csv"
];
const MAX_DECODED_BYTES = 20 * 1024 * 1024; // 20 MB

function doPost(e) {
  try {
    if (!SYNC_SECRET || SYNC_SECRET === "CHANGE_TO_YOUR_SECRET_KEY" || SYNC_SECRET.length < 6) {
      return jsonResponse({ ok: false, error: "Please set a custom SYNC_SECRET inside Apps Script first." });
    }
    if (!e || !e.postData || !e.postData.contents) {
      return jsonResponse({ ok: false, error: "Empty request body." });
    }

    const body = JSON.parse(e.postData.contents);
    if (!constantTimeEquals(String(body.secret || ""), SYNC_SECRET)) {
      return jsonResponse({ ok: false, error: "Invalid Sync Secret Key." });
    }

    const rootFolder = resolveRootFolder(String(body.rootFolderId || ""));
    const action = String(body.action || "");

    if (action === "ping") {
      return jsonResponse({
        ok: true,
        folderId: rootFolder.getId(),
        folderName: rootFolder.getName()
      });
    }

    if (action === "upload") {
      const fileName = sanitizeName(String(body.fileName || ""));
      const mimeType = String(body.mimeType || "application/octet-stream");
      const base64Data = String(body.base64Data || "");
      const subFolders = Array.isArray(body.subFolders) ? body.subFolders : [];

      if (!fileName) {
        return jsonResponse({ ok: false, error: "Missing fileName." });
      }
      if (ALLOWED_MIME_TYPES.indexOf(mimeType) === -1) {
        return jsonResponse({ ok: false, error: "Unsupported file type: " + mimeType });
      }
      if (!base64Data) {
        return jsonResponse({ ok: false, error: "Missing file content." });
      }

      const decodedBytes = Utilities.base64Decode(base64Data);
      if (decodedBytes.length > MAX_DECODED_BYTES) {
        return jsonResponse({ ok: false, error: "File exceeds 20 MB limit." });
      }

      let targetFolder = rootFolder;
      for (let i = 0; i < subFolders.length; i++) {
        const seg = sanitizeName(String(subFolders[i] || ""));
        if (seg) {
          targetFolder = getOrCreateSubFolder(targetFolder, seg);
        }
      }

      // Overwrite existing file with the same name (keeps revised <BillNo>.pdf clean with no duplicates)
      const existing = targetFolder.getFilesByName(fileName);
      while (existing.hasNext()) {
        existing.next().setTrashed(true);
      }

      const blob = Utilities.newBlob(decodedBytes, mimeType, fileName);
      const created = targetFolder.createFile(blob);

      return jsonResponse({
        ok: true,
        fileId: created.getId(),
        fileName: created.getName(),
        folderName: targetFolder.getName()
      });
    }

    return jsonResponse({ ok: false, error: "Unknown action: " + action });
  } catch (err) {
    return jsonResponse({ ok: false, error: String(err && err.message ? err.message : err) });
  }
}

function resolveRootFolder(overrideId) {
  const folderId = (overrideId && overrideId.trim()) || (DEFAULT_ROOT_FOLDER_ID && DEFAULT_ROOT_FOLDER_ID.trim());
  if (folderId) {
    return DriveApp.getFolderById(folderId);
  }
  const existing = DriveApp.getFoldersByName(DEFAULT_ROOT_FOLDER_NAME);
  if (existing.hasNext()) {
    return existing.next();
  }
  return DriveApp.createFolder(DEFAULT_ROOT_FOLDER_NAME);
}

function getOrCreateSubFolder(parent, name) {
  const it = parent.getFoldersByName(name);
  if (it.hasNext()) {
    return it.next();
  }
  return parent.createFolder(name);
}

function sanitizeName(raw) {
  return raw
    .replace(/\.\./g, "")
    .replace(/[\\\/:*?"<>|\r\n]+/g, "_")
    .trim()
    .slice(0, 120);
}

function constantTimeEquals(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return diff === 0;
}

function jsonResponse(obj) {
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
