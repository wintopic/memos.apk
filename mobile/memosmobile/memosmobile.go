//go:build android

package memosmobile

import (
	"context"
	"fmt"
	"path/filepath"
	"sync"

	"github.com/pkg/errors"

	"github.com/usememos/memos/internal/profile"
	"github.com/usememos/memos/internal/version"
	"github.com/usememos/memos/server"
	"github.com/usememos/memos/store"
	"github.com/usememos/memos/store/db"
)

const loopbackHost = "127.0.0.1"

type runningServer struct {
	cancel  context.CancelFunc
	server  *server.Server
	dataDir string
	port    int32
	baseURL string
}

var (
	runningServerMu sync.Mutex
	currentServer   *runningServer
)

// Version returns the current Memos version.
func Version() string {
	return version.GetCurrentVersion()
}

// Start boots a local Memos server for the Android app and returns its base URL.
func Start(dataDir string, port int32) (string, error) {
	if dataDir == "" {
		return "", errors.New("dataDir is required")
	}
	if port <= 0 {
		return "", errors.New("port must be greater than zero")
	}

	absoluteDataDir, err := filepath.Abs(dataDir)
	if err != nil {
		return "", errors.Wrap(err, "failed to resolve dataDir")
	}

	runningServerMu.Lock()
	defer runningServerMu.Unlock()

	if currentServer != nil {
		if currentServer.dataDir == absoluteDataDir && currentServer.port == port {
			return currentServer.baseURL, nil
		}
		stopLocked()
	}

	baseURL := fmt.Sprintf("http://%s:%d", loopbackHost, port)
	ctx, cancel := context.WithCancel(context.Background())
	instanceProfile := &profile.Profile{
		Addr:        loopbackHost,
		Port:        int(port),
		Data:        absoluteDataDir,
		Driver:      "sqlite",
		InstanceURL: baseURL,
		Version:     version.GetCurrentVersion(),
	}

	dbDriver, storeInstance, memosServer, err := newServer(ctx, instanceProfile)
	if err != nil {
		cancel()
		if storeInstance != nil {
			_ = storeInstance.Close()
		} else if dbDriver != nil {
			_ = dbDriver.Close()
		}
		return "", err
	}

	if err := memosServer.Start(ctx); err != nil {
		cancel()
		memosServer.Shutdown(context.Background())
		return "", errors.Wrap(err, "failed to start memos server")
	}

	currentServer = &runningServer{
		cancel:  cancel,
		server:  memosServer,
		dataDir: absoluteDataDir,
		port:    port,
		baseURL: baseURL,
	}

	return baseURL, nil
}

// Stop gracefully shuts down the running local Memos server.
func Stop() {
	runningServerMu.Lock()
	defer runningServerMu.Unlock()

	stopLocked()
}

func newServer(ctx context.Context, instanceProfile *profile.Profile) (store.Driver, *store.Store, *server.Server, error) {
	if err := instanceProfile.Validate(); err != nil {
		return nil, nil, nil, errors.Wrap(err, "failed to validate profile")
	}

	dbDriver, err := db.NewDBDriver(instanceProfile)
	if err != nil {
		return nil, nil, nil, errors.Wrap(err, "failed to create db driver")
	}

	storeInstance := store.New(dbDriver, instanceProfile)
	if err := storeInstance.Migrate(ctx); err != nil {
		return dbDriver, storeInstance, nil, errors.Wrap(err, "failed to migrate store")
	}

	memosServer, err := server.NewServer(ctx, instanceProfile, storeInstance)
	if err != nil {
		return dbDriver, storeInstance, nil, errors.Wrap(err, "failed to create server")
	}

	return dbDriver, storeInstance, memosServer, nil
}

func stopLocked() {
	if currentServer == nil {
		return
	}

	currentServer.server.Shutdown(context.Background())
	currentServer.cancel()
	currentServer = nil
}
