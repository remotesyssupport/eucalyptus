/*
 * Software License Agreement (BSD License)
 *
 * Copyright (c) 2008, Regents of the University of California
 * All rights reserved.
 *
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 *
 * * Redistributions of source code must retain the above
 *   copyright notice, this list of conditions and the
 *   following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the
 *   following disclaimer in the documentation and/or other
 *   materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * Author: Sunil Soman sunils@cs.ucsb.edu
 */

package edu.ucsb.eucalyptus.cloud.ws;

import com.eucalyptus.util.EntityWrapper;
import com.eucalyptus.util.EucalyptusCloudException;
import com.eucalyptus.util.EucalyptusProperties;

import edu.ucsb.eucalyptus.cloud.AccessDeniedException;
import edu.ucsb.eucalyptus.cloud.NotImplementedException;
import edu.ucsb.eucalyptus.msgs.*;
import edu.ucsb.eucalyptus.storage.StorageManager;
import edu.ucsb.eucalyptus.storage.fs.FileSystemStorageManager;
import edu.ucsb.eucalyptus.util.WalrusDataMessenger;
import com.eucalyptus.util.WalrusProperties;
import org.apache.log4j.Logger;
import  edu.ucsb.eucalyptus.cloud.entities.WalrusInfo;

public class WalrusControl {

	private static Logger LOG = Logger.getLogger( WalrusControl.class );

	private static WalrusDataMessenger imageMessenger = new WalrusDataMessenger();
	private static StorageManager storageManager;
	private static WalrusManager walrusManager;
	private static WalrusBlockStorageManager walrusBlockStorageManager;
	private static WalrusImageManager walrusImageManager;

	static {
		configure();
		storageManager = new FileSystemStorageManager(WalrusProperties.bucketRootDirectory);
		walrusImageManager = new WalrusImageManager(storageManager, imageMessenger);
		walrusManager = new WalrusManager(storageManager, walrusImageManager);
		walrusBlockStorageManager = new WalrusBlockStorageManager(storageManager, walrusManager);
		String limits = System.getProperty(WalrusProperties.USAGE_LIMITS_PROPERTY);
		if(limits != null) {
			WalrusProperties.shouldEnforceUsageLimits = Boolean.parseBoolean(limits);
		}
		walrusManager.initialize();
		Tracker.initialize();
		if(System.getProperty("euca.virtualhosting.disable") != null) {
			WalrusProperties.enableVirtualHosting = false;
		}
	}

	public WalrusControl() {}
	
	private static void configure() {
		WalrusInfo walrusInfo = getConfig();
		WalrusProperties.NAME = walrusInfo.getName();
		WalrusProperties.MAX_BUCKETS_PER_USER = walrusInfo.getStorageMaxBucketsPerUser();
		WalrusProperties.MAX_BUCKET_SIZE = walrusInfo.getStorageMaxBucketSizeInMB() * WalrusProperties.M;
		WalrusProperties.bucketRootDirectory = walrusInfo.getStorageDir();
		WalrusProperties.IMAGE_CACHE_SIZE = walrusInfo.getStorageMaxCacheSizeInMB() * WalrusProperties.M;
		WalrusProperties.MAX_TOTAL_SNAPSHOT_SIZE = walrusInfo.getStorageMaxTotalSnapshotSizeInGb();
	}
	
	private static WalrusInfo getConfig() {
		EntityWrapper<WalrusInfo> db = new EntityWrapper<WalrusInfo>();
		WalrusInfo walrusInfo;
		try {
			walrusInfo = db.getUnique(new WalrusInfo());
		} catch(EucalyptusCloudException ex) {
			walrusInfo = new WalrusInfo(WalrusProperties.NAME, 
					WalrusProperties.bucketRootDirectory, 
					WalrusProperties.MAX_BUCKETS_PER_USER, 
					(int)(WalrusProperties.MAX_BUCKET_SIZE / WalrusProperties.M),
					(int)(WalrusProperties.IMAGE_CACHE_SIZE / WalrusProperties.M),
					WalrusProperties.MAX_TOTAL_SNAPSHOT_SIZE);
			db.add(walrusInfo);
		} finally {
			db.commit();
		}
		return walrusInfo;
	}
	
	public UpdateWalrusConfigurationResponseType UpdateWalrusConfiguration(UpdateWalrusConfigurationType request) throws EucalyptusCloudException {
		UpdateWalrusConfigurationResponseType reply = (UpdateWalrusConfigurationResponseType) request.getReply();
		if(EucalyptusProperties.NAME.equals(request.getEffectiveUserId()))
			throw new AccessDeniedException("Only admin can change walrus properties.");
		String name = request.getName();
		if(name != null)
			WalrusProperties.NAME = name;
		String rootDir = request.getBucketRootDirectory();
		if(rootDir != null) {
			WalrusProperties.bucketRootDirectory = rootDir;
			storageManager.setRootDirectory(rootDir);
		}
		Integer maxBucketsPerUser = request.getMaxBucketsPerUser();
		if(maxBucketsPerUser != null)
			WalrusProperties.MAX_BUCKETS_PER_USER = maxBucketsPerUser;
		Long maxBucketSize = request.getMaxBucketSize();
		if(maxBucketSize != null)
			WalrusProperties.MAX_BUCKET_SIZE = maxBucketSize;    	
		Long imageCacheSize = request.getImageCacheSize();
		if(imageCacheSize != null)
			WalrusProperties.IMAGE_CACHE_SIZE = imageCacheSize;
		Integer totalSnapshotSize = request.getTotalSnapshotSize();
		if(totalSnapshotSize != null)
			WalrusProperties.MAX_TOTAL_SNAPSHOT_SIZE = totalSnapshotSize;
		walrusManager.check();
		getConfig();
		return reply;
	}

	public CreateBucketResponseType CreateBucket(CreateBucketType request) throws EucalyptusCloudException {
		return walrusManager.createBucket(request);
	}

	public DeleteBucketResponseType DeleteBucket(DeleteBucketType request) throws EucalyptusCloudException {
		return walrusManager.deleteBucket(request);
	}

	public ListAllMyBucketsResponseType ListAllMyBuckets(ListAllMyBucketsType request) throws EucalyptusCloudException {
		return walrusManager.listAllMyBuckets(request);
	}

	public GetBucketAccessControlPolicyResponseType GetBucketAccessControlPolicy(GetBucketAccessControlPolicyType request) throws EucalyptusCloudException
	{
		return walrusManager.getBucketAccessControlPolicy(request);
	}

	public PutObjectResponseType PutObject (PutObjectType request) throws EucalyptusCloudException {
		return walrusManager.putObject(request);
	}

	public PostObjectResponseType PostObject (PostObjectType request) throws EucalyptusCloudException {
		return walrusManager.postObject(request);
	}

	public PutObjectInlineResponseType PutObjectInline (PutObjectInlineType request) throws EucalyptusCloudException {
		return walrusManager.putObjectInline(request);
	}

	public void AddObject (String userId, String bucketName, String key) throws EucalyptusCloudException {
		walrusManager.addObject(userId, bucketName, key);
	}

	public DeleteObjectResponseType DeleteObject (DeleteObjectType request) throws EucalyptusCloudException {
		return walrusManager.deleteObject(request);
	}

	public ListBucketResponseType ListBucket(ListBucketType request) throws EucalyptusCloudException {
		return walrusManager.listBucket(request);
	}

	public GetObjectAccessControlPolicyResponseType GetObjectAccessControlPolicy(GetObjectAccessControlPolicyType request) throws EucalyptusCloudException
	{
		return walrusManager.getObjectAccessControlPolicy(request);
	}

	public SetBucketAccessControlPolicyResponseType SetBucketAccessControlPolicy(SetBucketAccessControlPolicyType request) throws EucalyptusCloudException
	{
		return walrusManager.setBucketAccessControlPolicy(request);
	}

	public SetObjectAccessControlPolicyResponseType SetObjectAccessControlPolicy(SetObjectAccessControlPolicyType request) throws EucalyptusCloudException
	{
		return walrusManager.setObjectAccessControlPolicy(request);
	}

	public SetRESTBucketAccessControlPolicyResponseType SetRESTBucketAccessControlPolicy(SetRESTBucketAccessControlPolicyType request) throws EucalyptusCloudException
	{
		return walrusManager.setRESTBucketAccessControlPolicy(request);
	}

	public SetRESTObjectAccessControlPolicyResponseType SetRESTObjectAccessControlPolicy(SetRESTObjectAccessControlPolicyType request) throws EucalyptusCloudException
	{
		return walrusManager.setRESTObjectAccessControlPolicy(request);
	}

	public GetObjectResponseType GetObject(GetObjectType request) throws EucalyptusCloudException {
		return walrusManager.getObject(request);
	}

	public GetObjectExtendedResponseType GetObjectExtended(GetObjectExtendedType request) throws EucalyptusCloudException {
		return walrusManager.getObjectExtended(request);
	}

	public GetBucketLocationResponseType GetBucketLocation(GetBucketLocationType request) throws EucalyptusCloudException {
		return walrusManager.getBucketLocation(request);
	}

	public CopyObjectResponseType CopyObject(CopyObjectType request) throws EucalyptusCloudException {
		return walrusManager.copyObject(request);
	}

	public GetBucketLoggingStatusResponseType GetBucketLoggingStatus(GetBucketLoggingStatusType request) throws EucalyptusCloudException {
		GetBucketLoggingStatusResponseType reply = (GetBucketLoggingStatusResponseType) request.getReply();

		throw new NotImplementedException("GetBucketLoggingStatus");
	}

	public SetBucketLoggingStatusResponseType SetBucketLoggingStatus(SetBucketLoggingStatusType request) throws EucalyptusCloudException {
		SetBucketLoggingStatusResponseType reply = (SetBucketLoggingStatusResponseType) request.getReply();

		throw new NotImplementedException("SetBucketLoggingStatus");
	}

	public GetDecryptedImageResponseType GetDecryptedImage(GetDecryptedImageType request) throws EucalyptusCloudException {
		return walrusImageManager.getDecryptedImage(request);
	}

	public CheckImageResponseType CheckImage(CheckImageType request) throws EucalyptusCloudException {
		return walrusImageManager.checkImage(request);
	}

	public CacheImageResponseType CacheImage(CacheImageType request) throws EucalyptusCloudException {
		return walrusImageManager.cacheImage(request);
	}

	public FlushCachedImageResponseType FlushCachedImage(FlushCachedImageType request) throws EucalyptusCloudException {
		return walrusImageManager.flushCachedImage(request);
	}

	public StoreSnapshotResponseType StoreSnapshot(StoreSnapshotType request) throws EucalyptusCloudException {
		return walrusBlockStorageManager.storeSnapshot(request);
	}

	public GetWalrusSnapshotResponseType GetWalrusSnapshot(GetWalrusSnapshotType request) throws EucalyptusCloudException {
		return walrusBlockStorageManager.getSnapshot(request);
	}

	public DeleteWalrusSnapshotResponseType DeleteWalrusSnapshot(DeleteWalrusSnapshotType request) throws EucalyptusCloudException {
		return walrusBlockStorageManager.deleteWalrusSnapshot(request);
	}

}