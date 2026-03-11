package com.hacaller.hamediaclient

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.io.FileOutputStream
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.StreamCopier
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import net.schmizz.sshj.xfer.FileSystemFile
import net.schmizz.sshj.xfer.TransferListener


fun downloadFromServer(
    serverIp: String,
    shareName: String,
    filePath: String, // e.g., "Movies/video.mp4"
    destFile: File,
    user: String = "guest",
    pass: String = ""
) {
    val client = SMBClient()
    client.connect(serverIp).use { connection ->
        val auth = AuthenticationContext(user, pass.toCharArray(), "")
        val session = connection.authenticate(auth)

        (session.connectShare(shareName) as DiskShare).use { share ->
            val remoteFile = share.openFile(filePath, setOf(AccessMask.GENERIC_READ), null, SMB2ShareAccess.ALL, SMB2CreateDisposition.FILE_OPEN, null)
            remoteFile.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
    }
}

fun downloadFromDebian(
    host: String,
    user: String,
    pass: String,
    remotePath: String, // e.g., "/mnt/data/media/movie.mkv"
    localFile: File
) {
    val ssh = SSHClient()
    // NOTE: PromiscuousVerifier skips host key check (fine for home Wi-Fi)
    ssh.addHostKeyVerifier(PromiscuousVerifier())

    ssh.connect(host)
    try {
        ssh.authPassword(user, pass)
        val sftp = ssh.newSFTPClient()
        sftp.use { client ->
            // This pulls the file directly from Debian to your Android storage
            client.get(remotePath, localFile.absolutePath)
        }
    } finally {
        ssh.disconnect()
    }
}

fun uploadToDebian(
    host: String,
    user: String,
    pass: String,
    localFile: File,
    remoteDir: String,
    onProgress: (Float) -> Unit = {}
) {
    val ssh = SSHClient()
    ssh.addHostKeyVerifier(PromiscuousVerifier())
    ssh.connect(host)
    try {
        ssh.authPassword(user, pass)
        val sftp = ssh.newSFTPClient()
        sftp.use { client ->
            client.fileTransfer.setTransferListener(object : TransferListener {
                override fun directory(name: String?): TransferListener = this
                override fun file(name: String?, size: Long): StreamCopier.Listener {
                    return StreamCopier.Listener { transferred ->
                        if (size > 0) {
                            onProgress(transferred.toFloat() / size)
                        }
                    }
                }
            })
            val remotePath = if (remoteDir.endsWith("/")) "$remoteDir${localFile.name}" else "$remoteDir/${localFile.name}"
            client.fileTransfer.upload(FileSystemFile(localFile), remotePath)
        }
    } finally {
        ssh.disconnect()
    }
}

fun fetchFileList(host: String, user: String, pass: String, remoteDir: String): List<RemoteFile> {
    val ssh = SSHClient()
    ssh.addHostKeyVerifier(PromiscuousVerifier())
    ssh.connect(host)
    return try {
        ssh.authPassword(user, pass)
        val sftp = ssh.newSFTPClient()
        val files = sftp.ls(remoteDir).map { res ->
            RemoteFile(res.name, res.path, res.isDirectory)
        }.filter { !it.name.startsWith(".") } // Filter out hidden files and Linux navigation dots
        sftp.close()
        files
    } finally {
        ssh.disconnect()
    }
}

fun downloadWithProgress(
    host: String, user: String, pass: String,
    remotePath: String, localFile: File,
    onProgress: (Float) -> Unit // Callback for 0.0 to 1.0
) {
    val ssh = SSHClient()
    ssh.addHostKeyVerifier(PromiscuousVerifier())
    ssh.connect(host)
    try {
        ssh.authPassword(user, pass)
        val sftp = ssh.newSFTPClient()
        sftp.use { client ->
            client.fileTransfer.setTransferListener(object : TransferListener {
                override fun directory(name: String?): TransferListener {
                    return this
                }

                override fun file(name: String?, size: Long): StreamCopier.Listener {
                    return StreamCopier.Listener { transferred ->
                        if (size > 0) {
                            onProgress(transferred.toFloat() / size)
                        }
                    }
                }
            })
            client.fileTransfer.download(remotePath, FileSystemFile(localFile))
        }
    } finally {
        ssh.disconnect()
    }
}
