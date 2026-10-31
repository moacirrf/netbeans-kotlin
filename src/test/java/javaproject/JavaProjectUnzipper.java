package javaproject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jetbrains.kotlin.utils.ProjectUtils;
import org.netbeans.junit.NbTestCase;
import org.openide.filesystems.FileLock;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;

/**
 *
 * @author Alexander.Baratynski
 */
public class JavaProjectUnzipper extends NbTestCase {
    
    public static JavaProjectUnzipper INSTANCE = new JavaProjectUnzipper("ProjectUnzipper");
    
    private JavaProjectUnzipper(String name) {
        super(name);
    }
    
    private void unZipFile(File archiveFile, FileObject destDir) throws IOException {
        FileInputStream fis = new FileInputStream(archiveFile);
        try {
            ZipInputStream str = new ZipInputStream(fis);
            ZipEntry entry;
            while ((entry = str.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    FileUtil.createFolder(destDir, entry.getName());
                } else {
                    FileObject fo = FileUtil.createData(destDir, entry.getName());
                    FileLock lock = fo.lock();
                    try {
                        OutputStream out = fo.getOutputStream(lock);
                        try {
                            FileUtil.copy(str, out);
                        } finally {
                            out.close();
                        }
                    } finally {
                        lock.releaseLock();
                    }
                }
            }
        } finally {
            fis.close();
        }

    }
    
    private FileObject getUnzippedFolder(String path) throws IOException {
        assertNotNull(path);
        FileObject destFileObject = FileUtil.toFileObject(getWorkDir());
        String folderName = "kotlinc";
        FileObject unzippedFolder = destFileObject.getFileObject(folderName);
        if (unzippedFolder != null && unzippedFolder.isValid()) {
            return unzippedFolder;
        }

        File archiveFile = new File(path);
        unZipFile(archiveFile, destFileObject);
        assertTrue(destFileObject.isValid());
        
        unzippedFolder = destFileObject.getFileObject(folderName);
        assertNotNull(unzippedFolder);
        
        return unzippedFolder;
    }
    
    public FileObject getTestProject() throws IOException {
        getKtHomeForTests();
        String path = "." + ProjectUtils.FILE_SEPARATOR + "src" + ProjectUtils.FILE_SEPARATOR +
                "test" + ProjectUtils.FILE_SEPARATOR + "resources" + ProjectUtils.FILE_SEPARATOR + "projForTest";
        
        File projectFile = new File(path);
        FileObject projectFileObject = FileUtil.toFileObject(FileUtil.normalizeFile(projectFile));
        FileObject destFileObject = FileUtil.toFileObject(getWorkDir());
        
        FileObject testProject = destFileObject.getFileObject("projForTest");
        if (testProject != null) {
            testProject.delete();
        }
        
        testProject = FileUtil.copyFile(projectFileObject, destFileObject, "projForTest");
        
        return testProject;
    }
    
    private void getKtHomeForTests() throws IOException {
        if (ProjectUtils.KT_HOME != null && new File(ProjectUtils.KT_HOME).isDirectory()) {
            return;
        }
        File workDir = getWorkDir();
        File kotlincDir = new File(workDir, "kotlinc");
        if (!kotlincDir.exists()) {
            kotlincDir.mkdirs();
        }
        ProjectUtils.KT_HOME = kotlincDir.getAbsolutePath() + ProjectUtils.FILE_SEPARATOR;
    }
}
