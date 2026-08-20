package com.example.arlacontrole.data.repository;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.example.arlacontrole.data.local.ArlaDatabase;
import com.example.arlacontrole.data.local.DriverDao;
import com.example.arlacontrole.data.local.DriverEntity;
import com.example.arlacontrole.data.local.OdometerCalibrationDao;
import com.example.arlacontrole.data.local.OdometerCalibrationEntity;
import com.example.arlacontrole.data.local.OdometerCalibrationMediaDao;
import com.example.arlacontrole.data.local.OdometerCalibrationMediaEntity;
import com.example.arlacontrole.data.local.RefuelDao;
import com.example.arlacontrole.data.local.RefuelEntity;
import com.example.arlacontrole.data.local.SafetyEventDao;
import com.example.arlacontrole.data.local.SafetyEventEntity;
import com.example.arlacontrole.data.local.SyncQueueDao;
import com.example.arlacontrole.data.local.SyncQueueEntity;
import com.example.arlacontrole.data.local.VehicleDao;
import com.example.arlacontrole.data.local.VehicleEntity;
import com.example.arlacontrole.data.remote.ApiClientFactory;
import com.example.arlacontrole.data.remote.ApiService;
import com.example.arlacontrole.data.remote.AuthResponse;
import com.example.arlacontrole.data.remote.HealthResponse;
import com.example.arlacontrole.data.remote.LoginRequest;
import com.example.arlacontrole.data.remote.RecordCreateRequest;
import com.example.arlacontrole.data.remote.RecordResponse;
import com.example.arlacontrole.data.remote.VehicleResponse;
import com.example.arlacontrole.evidence.RefuelEvidenceManager;
import com.example.arlacontrole.importer.SafetySpreadsheetImporter;
import com.example.arlacontrole.model.AuthSession;
import com.example.arlacontrole.model.CalibrationMediaType;
import com.example.arlacontrole.model.CostValidationResult;
import com.example.arlacontrole.model.FuelCostContext;
import com.example.arlacontrole.model.FuelCostSnapshot;
import com.example.arlacontrole.model.NewCalibrationInput;
import com.example.arlacontrole.model.NewRefuelInput;
import com.example.arlacontrole.model.NewSafetyEventInput;
import com.example.arlacontrole.model.RefuelOdometerContext;
import com.example.arlacontrole.model.RefuelEntryMode;
import com.example.arlacontrole.model.RefuelStatus;
import com.example.arlacontrole.model.SafetyAnalysisStatus;
import com.example.arlacontrole.model.SafetyEventType;
import com.example.arlacontrole.model.SafetyImportResult;
import com.example.arlacontrole.model.SafetySeverity;
import com.example.arlacontrole.model.SyncExecutionResult;
import com.example.arlacontrole.model.SyncState;
import com.example.arlacontrole.model.UserRole;
import com.example.arlacontrole.rules.CostValidator;
import com.example.arlacontrole.rules.FuelEvaluation;
import com.example.arlacontrole.rules.FuelCostCalculator;
import com.example.arlacontrole.rules.FuelRulesEngine;
import com.example.arlacontrole.sync.SyncScheduler;
import com.example.arlacontrole.utils.AppPreferences;
import com.example.arlacontrole.utils.FormatUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import retrofit2.Response;

public class ArlaRepository {

    private static final String SYNC_OPERATION_CREATE = "CREATE";
    private static final String IMPORTED_EVIDENCE_MARKER = "imported://evidence";
    private static final DateTimeFormatter IMPORTED_DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter IMPORTED_DATE_TIME_SECONDS = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final DateTimeFormatter IMPORTED_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final Context appContext;
    private final DriverDao driverDao;
    private final VehicleDao vehicleDao;
    private final RefuelDao refuelDao;
    private final SafetyEventDao safetyEventDao;
    private final OdometerCalibrationDao odometerCalibrationDao;
    private final OdometerCalibrationMediaDao odometerCalibrationMediaDao;
    private final SyncQueueDao syncQueueDao;
    private final AppPreferences preferences;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ArlaRepository(Context context, ArlaDatabase database) {
        appContext = context.getApplicationContext();
        driverDao = database.driverDao();
        vehicleDao = database.vehicleDao();
        refuelDao = database.refuelDao();
        safetyEventDao = database.safetyEventDao();
        odometerCalibrationDao = database.odometerCalibrationDao();
        odometerCalibrationMediaDao = database.odometerCalibrationMediaDao();
        syncQueueDao = database.syncQueueDao();
        preferences = new AppPreferences(appContext);
    }

    public LiveData<List<DriverEntity>> observeDrivers() {
        return driverDao.observeActiveDrivers();
    }

    public LiveData<List<VehicleEntity>> observeVehicles() {
        return vehicleDao.observeVehicles();
    }

    public LiveData<List<RefuelEntity>> observeAllRefuels() {
        return refuelDao.observeAll();
    }

    public LiveData<List<RefuelEntity>> observeRecentRefuels(int limit) {
        return refuelDao.observeRecent(limit);
    }

    public LiveData<List<SafetyEventEntity>> observeAllSafetyEvents() {
        return safetyEventDao.observeAll();
    }

    public LiveData<List<SafetyEventEntity>> observeRecentSafetyEvents(int limit) {
        return safetyEventDao.observeRecent(limit);
    }

    public LiveData<SafetyEventEntity> observeSafetyEvent(long localId) {
        return safetyEventDao.observeById(localId);
    }

    public LiveData<Integer> observeOpenSafetyEventCount() {
        return safetyEventDao.observeOpenCount();
    }

    public LiveData<VehicleEntity> observeVehicle(String plate) {
        return vehicleDao.observeByPlate(plate);
    }

    public LiveData<List<RefuelEntity>> observeVehicleHistory(String plate) {
        return refuelDao.observeByVehicle(plate);
    }

    public LiveData<RefuelEntity> observeRefuel(long localId) {
        return refuelDao.observeById(localId);
    }

    public LiveData<OdometerCalibrationEntity> observeLatestCalibration() {
        return odometerCalibrationDao.observeLatest();
    }

    public LiveData<OdometerCalibrationEntity> observeCalibration(long localId) {
        return odometerCalibrationDao.observeById(localId);
    }

    public LiveData<List<OdometerCalibrationMediaEntity>> observeCalibrationMedia(long calibrationLocalId) {
        return odometerCalibrationMediaDao.observeByCalibration(calibrationLocalId);
    }

    public LiveData<Integer> observePendingSyncCount() {
        return refuelDao.observePendingCount();
    }

    public String getApiBaseUrl() {
        return preferences.getApiBaseUrl();
    }

    public void saveApiBaseUrl(String url) {
        preferences.saveApiBaseUrl(url);
    }

    public long getLastSyncAt() {
        return preferences.getLastSyncAt();
    }

    public String getLastSyncMessage() {
        return preferences.getLastSyncMessage();
    }

    public AuthSession getCurrentSession() {
        return preferences.getSession();
    }

    public boolean hasValidSession() {
        return preferences.hasValidSession();
    }

    public void login(String email, String password, RepositoryCallback<AuthSession> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                if (!AppPreferences.isValidBaseUrl(preferences.getApiBaseUrl())) {
                    postError(callback, "URL da API invalida.");
                    return;
                }

                ApiService service = ApiClientFactory.create(preferences.getApiBaseUrl(), preferences);
                LoginRequest request = new LoginRequest();
                request.email = email == null ? "" : email.trim();
                request.password = password == null ? "" : password;

                Response<AuthResponse> response = service.login(request).execute();
                if (!response.isSuccessful() || response.body() == null || response.body().user == null) {
                    postError(callback, "Nao foi possivel autenticar.");
                    return;
                }

                AuthSession newSession = mapSession(response.body());
                AuthSession previousSession = preferences.getSession();
                preferences.saveSession(newSession);
                ensureDriverExists(newSession.linkedDriverName);

                if (previousSession.userId > 0L && previousSession.userId != newSession.userId) {
                    clearLocalSessionData();
                }

                synchronizeBlocking();
                postSuccess(callback, newSession);
            } catch (IOException exception) {
                postError(callback, exception.getMessage() == null ? "Falha de rede ao autenticar." : exception.getMessage());
            } catch (Exception exception) {
                postError(callback, exception.getMessage() == null ? "Falha ao iniciar sessao." : exception.getMessage());
            }
        });
    }

    public void logout(RepositoryCallback<Void> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                if (refuelDao.getPendingCountSync() > 0) {
                    postError(callback, "Existem registros pendentes de sincronizacao. Sincronize antes de sair.");
                    return;
                }

                if (hasValidSession() && AppPreferences.isValidBaseUrl(preferences.getApiBaseUrl())) {
                    try {
                        ApiClientFactory.create(preferences.getApiBaseUrl(), preferences).logout().execute();
                    } catch (Exception ignored) {
                    }
                }

                preferences.clearSession();
                clearLocalSessionData();
                postSuccess(callback, null);
            } catch (Exception exception) {
                postError(callback, exception.getMessage() == null ? "Falha ao encerrar sessao." : exception.getMessage());
            }
        });
    }

    public void createRefuel(NewRefuelInput input, RepositoryCallback<Long> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                VehicleEntity vehicle = vehicleDao.findByPlateSync(input.vehiclePlate.trim().toUpperCase());
                if (vehicle == null) {
                    postError(callback, "Veiculo nao encontrado.");
                    return;
                }
                if (input.evidencePhotoPath == null || input.evidencePhotoPath.trim().isEmpty() || !new File(input.evidencePhotoPath).exists()) {
                    postError(callback, "A foto de evidencia e obrigatoria para salvar o abastecimento.");
                    return;
                }
                if (input.checklistPayload == null || input.checklistPayload.trim().isEmpty() || input.checklistCompletedAtIso == null || input.checklistCompletedAtIso.trim().isEmpty()) {
                    postError(callback, "O checklist operacional deve ser concluido antes do abastecimento.");
                    return;
                }

                DriverEntity driver = resolveDriverForCurrentSession(input.driverId);
                if (driver == null) {
                    postError(callback, "Motorista nao encontrado.");
                    return;
                }

                RefuelEntity previousRecord = refuelDao.getLatestForVehicleAndFuelSync(vehicle.plate, input.fuelType);
                FuelCostSnapshot costSnapshot = FuelCostCalculator.normalize(
                    input.liters,
                    input.totalAmount,
                    input.pricePerLiter,
                    input.odometerInitialKm,
                    input.odometerFinalKm
                );
                CostValidationResult costValidation = CostValidator.validate(input.fuelType, costSnapshot, previousRecord);
                if (!costValidation.valid) {
                    postError(callback, costValidation.message);
                    return;
                }
                FuelEvaluation evaluation = FuelRulesEngine.evaluate(input.fuelType, vehicle, input.liters, input.odometerFinalKm, previousRecord);
                if (RefuelStatus.priority(costValidation.level) > RefuelStatus.priority(evaluation.status)) {
                    evaluation.status = costValidation.level;
                }
                if (costValidation.message != null && !costValidation.message.trim().isEmpty()) {
                    evaluation.reason = appendReason(evaluation.reason, costValidation.message);
                }
                if (input.odometerDivergenceKm != null && input.odometerDivergenceKm >= 100) {
                    if (RefuelStatus.NORMAL.equals(evaluation.status)) {
                        evaluation.status = RefuelStatus.ATTENTION;
                    }
                    evaluation.reason = appendReason(evaluation.reason, "Divergencia entre odometro inicial informado e ultimo final registrado.");
                }
                long now = System.currentTimeMillis();

                RefuelEntity entity = new RefuelEntity(
                    input.fuelType,
                    UUID.randomUUID().toString(),
                    null,
                    vehicle.plate,
                    vehicle.fleetCode,
                    vehicle.model,
                    driver.id,
                    driver.name,
                    input.liters,
                    input.odometerFinalKm,
                    input.suppliedAtIso,
                    input.locationName.trim(),
                    input.totalAmount,
                    input.notes == null ? "" : input.notes.trim(),
                    input.evidencePhotoPath == null ? "" : input.evidencePhotoPath.trim(),
                    input.evidenceCategory == null ? "" : input.evidenceCategory.trim(),
                    input.checklistPayload == null ? "" : input.checklistPayload,
                    input.checklistCompletedAtIso == null ? "" : input.checklistCompletedAtIso,
                    input.dataEntryMode == null || input.dataEntryMode.trim().isEmpty() ? RefuelEntryMode.MANUAL : input.dataEntryMode,
                    input.ocrStatus == null ? "" : input.ocrStatus,
                    input.ocrRawText == null ? "" : input.ocrRawText,
                    input.ocrMetadataJson == null ? "" : input.ocrMetadataJson,
                    evaluation.status,
                    evaluation.reason,
                    evaluation.kmSinceLastSupply,
                    evaluation.litersPer1000Km,
                    evaluation.kmPerLiter,
                    SyncState.PENDING,
                    "",
                    now,
                    null
                );
                entity.odometerInitialKm = input.odometerInitialKm;
                entity.odometerFinalKm = input.odometerFinalKm;
                entity.calculatedArlaControlQuantity = input.calculatedArlaControlQuantity;
                entity.expectedInitialOdometerKm = input.expectedInitialOdometerKm;
                entity.odometerDivergenceKm = input.odometerDivergenceKm;
                entity.odometerKm = input.odometerFinalKm;
                entity.totalAmount = costSnapshot.totalAmount;
                entity.pricePerLiter = costSnapshot.pricePerLiter == null ? 0d : costSnapshot.pricePerLiter;
                entity.costPerKm = costSnapshot.costPerKm == null ? 0d : costSnapshot.costPerKm;

                long localId = refuelDao.insert(entity);
                syncQueueDao.insert(new SyncQueueEntity(localId, SYNC_OPERATION_CREATE, 0, now, 0));
                SyncScheduler.enqueueImmediate(appContext);
                postSuccess(callback, localId);
            } catch (Exception exception) {
                postError(callback, exception.getMessage() == null ? "Falha ao salvar abastecimento." : exception.getMessage());
            }
        });
    }

    public void loadRefuelOdometerContext(String vehiclePlate, RepositoryCallback<RefuelOdometerContext> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                RefuelOdometerContext result = new RefuelOdometerContext();
                if (vehiclePlate != null && !vehiclePlate.trim().isEmpty()) {
                    RefuelEntity latestRefuel = refuelDao.getLatestForVehicleSync(vehiclePlate.trim().toUpperCase(Locale.ROOT));
                    if (latestRefuel != null) {
                        int latestFinal = latestRefuel.odometerFinalKm > 0 ? latestRefuel.odometerFinalKm : latestRefuel.odometerKm;
                        if (latestFinal > 0) {
                            result.hasPreviousRecord = true;
                            result.expectedInitialOdometerKm = latestFinal;
                            result.sourceSuppliedAtIso = latestRefuel.suppliedAtIso;
                        }
                    }
                }
                postSuccess(callback, result);
            } catch (Exception exception) {
                postError(callback, exception.getMessage() == null ? "Nao foi possivel carregar o ultimo odometro do veiculo." : exception.getMessage());
            }
        });
    }

    public void loadFuelCostContext(String fuelType, String vehiclePlate, RepositoryCallback<FuelCostContext> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                FuelCostContext result = new FuelCostContext();
                if (vehiclePlate != null && !vehiclePlate.trim().isEmpty() && fuelType != null && !fuelType.trim().isEmpty()) {
                    RefuelEntity latestVehicleFuel = refuelDao.getLatestForVehicleAndFuelSync(vehiclePlate.trim().toUpperCase(Locale.ROOT), fuelType.trim().toUpperCase(Locale.ROOT));
                    if (latestVehicleFuel != null && latestVehicleFuel.pricePerLiter > 0d) {
                        result.suggestedPricePerLiter = latestVehicleFuel.pricePerLiter;
                        result.lastReferenceAtIso = latestVehicleFuel.suppliedAtIso;
                        result.hasVehicleSuggestion = true;
                    }
                    List<RefuelEntity> fuelRecords = refuelDao.getByFuelTypeSync(fuelType.trim().toUpperCase(Locale.ROOT));
                    double totalPrice = 0d;
                    int priceCount = 0;
                    if (fuelRecords != null) {
                        for (RefuelEntity entity : fuelRecords) {
                            if (entity.pricePerLiter > 0d) {
                                totalPrice += entity.pricePerLiter;
                                priceCount++;
                                if (!result.hasVehicleSuggestion && result.suggestedPricePerLiter == null) {
                                    result.suggestedPricePerLiter = entity.pricePerLiter;
                                    result.lastReferenceAtIso = entity.suppliedAtIso;
                                }
                            }
                        }
                    }
                    result.averagePricePerLiter = priceCount == 0 ? null : totalPrice / priceCount;
                }
                postSuccess(callback, result);
            } catch (Exception exception) {
                postError(callback, exception.getMessage() == null ? "Nao foi possivel carregar a referencia financeira do combustivel." : exception.getMessage());
            }
        });
    }

    public void createCalibration(NewCalibrationInput input, RepositoryCallback<Long> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                if (input.calibrationAtIso == null || input.calibrationAtIso.trim().isEmpty()) {
                    postError(callback, "Informe a data da afericao.");
                    return;
                }
                if (input.odometerKm <= 0) {
                    postError(callback, "Informe um odometro aferido valido.");
                    return;
                }
                if (input.registeredByName == null || input.registeredByName.trim().isEmpty()) {
                    postError(callback, "Selecione o responsavel pela afericao.");
                    return;
                }

                VehicleEntity vehicle = null;
                String normalizedPlate = input.vehiclePlate == null ? "" : input.vehiclePlate.trim().toUpperCase(Locale.ROOT);
                if (!normalizedPlate.isEmpty()) {
                    vehicle = vehicleDao.findByPlateSync(normalizedPlate);
                    if (vehicle == null) {
                        postError(callback, "Veiculo nao encontrado para registrar a afericao.");
                        return;
                    }
                }

                long now = System.currentTimeMillis();
                OdometerCalibrationEntity entity = new OdometerCalibrationEntity(
                    vehicle == null ? "" : vehicle.plate,
                    vehicle == null ? "" : vehicle.fleetCode,
                    vehicle == null ? "" : vehicle.model,
                    input.calibrationAtIso.trim(),
                    input.odometerKm,
                    input.notes == null ? "" : input.notes.trim(),
                    safeImportedText(input.registeredByName, "Operacao"),
                    LocalDateTime.now().withSecond(0).withNano(0).toString(),
                    SyncState.PENDING,
                    "",
                    now,
                    null
                );
                long calibrationLocalId = odometerCalibrationDao.insert(entity);

                List<OdometerCalibrationMediaEntity> mediaItems = new ArrayList<>();
                for (String photoPath : input.photoPaths) {
                    if (photoPath != null && !photoPath.trim().isEmpty()) {
                        mediaItems.add(new OdometerCalibrationMediaEntity(calibrationLocalId, CalibrationMediaType.PHOTO, photoPath.trim(), now));
                    }
                }
                for (String videoPath : input.videoPaths) {
                    if (videoPath != null && !videoPath.trim().isEmpty()) {
                        mediaItems.add(new OdometerCalibrationMediaEntity(calibrationLocalId, CalibrationMediaType.VIDEO, videoPath.trim(), now));
                    }
                }
                if (!mediaItems.isEmpty()) {
                    odometerCalibrationMediaDao.insertAll(mediaItems);
                }
                postSuccess(callback, calibrationLocalId);
            } catch (Exception exception) {
                postError(callback, exception.getMessage() == null ? "Falha ao salvar a afericao." : exception.getMessage());
            }
        });
    }

    public void createSafetyEvent(NewSafetyEventInput input, RepositoryCallback<Long> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                VehicleEntity vehicle = vehicleDao.findByPlateSync(input.vehiclePlate == null ? "" : input.vehiclePlate.trim().toUpperCase(Locale.US));
                if (vehicle == null) {
                    postError(callback, "Veiculo nao encontrado.");
                    return;
                }

                DriverEntity driver = resolveDriverForCurrentSession(input.driverId);
                if (driver == null) {
                    postError(callback, "Motorista nao encontrado.");
                    return;
                }

                String description = input.description == null ? "" : input.description.trim();
                if (description.isEmpty()) {
                    postError(callback, "Descreva a ocorrencia para salvar o evento.");
                    return;
                }

                String location = input.locationName == null ? "" : input.locationName.trim();
                if (location.isEmpty()) {
                    postError(callback, "Informe o local da ocorrencia.");
                    return;
                }

                long now = System.currentTimeMillis();
                SafetyEventEntity entity = new SafetyEventEntity(
                    UUID.randomUUID().toString(),
                    null,
                    input.type == null || input.type.trim().isEmpty() ? "INCIDENTE" : input.type.trim(),
                    input.occurredAtIso == null || input.occurredAtIso.trim().isEmpty() ? LocalDateTime.now().withSecond(0).withNano(0).toString() : input.occurredAtIso.trim(),
                    vehicle.plate,
                    vehicle.fleetCode,
                    vehicle.model,
                    driver.id,
                    driver.name,
                    location,
                    description,
                    input.severity == null || input.severity.trim().isEmpty() ? SafetySeverity.MODERATE : input.severity.trim(),
                    input.probableCause == null ? "" : input.probableCause.trim(),
                    input.notes == null ? "" : input.notes.trim(),
                    input.evidencePhotoPath == null ? "" : input.evidencePhotoPath.trim(),
                    input.evidenceCategory == null ? "" : input.evidenceCategory.trim(),
                    input.analysisStatus == null || input.analysisStatus.trim().isEmpty() ? SafetyAnalysisStatus.OPEN : input.analysisStatus.trim(),
                    1,
                    false,
                    "",
                    now,
                    now
                );
                long localId = safetyEventDao.insert(entity);
                postSuccess(callback, localId);
            } catch (Exception exception) {
                postError(callback, exception.getMessage() == null ? "Falha ao salvar evento de seguranca." : exception.getMessage());
            }
        });
    }

    public void importSafetyEventsSpreadsheet(Uri fileUri, String fileName, RepositoryCallback<SafetyImportResult> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                AuthSession session = preferences.getSession();
                if (session == null || !UserRole.canImportSafetyOccurrences(session.role)) {
                    postError(callback, "A importacao de ocorrencias por planilha e exclusiva do usuario autorizado de seguranca.");
                    return;
                }
                if (fileUri == null) {
                    postError(callback, "Selecione um arquivo Excel valido para continuar.");
                    return;
                }

                try (InputStream inputStream = appContext.getContentResolver().openInputStream(fileUri)) {
                    if (inputStream == null) {
                        postError(callback, "Nao foi possivel abrir a planilha selecionada.");
                        return;
                    }

                    SafetySpreadsheetImporter importer = new SafetySpreadsheetImporter();
                    SafetySpreadsheetImporter.SpreadsheetData sheet = importer.read(inputStream, fileName);
                    SafetyImportResult result = new SafetyImportResult();
                    result.fileName = sheet.fileName == null ? "" : sheet.fileName;
                    result.rowsRead = sheet.rows.size();

                    List<SafetyEventEntity> entities = new ArrayList<>();
                    long now = System.currentTimeMillis();

                    for (SafetySpreadsheetImporter.SpreadsheetRow row : sheet.rows) {
                        String plate = sanitizePlate(row.get(SafetySpreadsheetImporter.COLUMN_PLATE));
                        if (plate.isEmpty()) {
                            result.skippedRows++;
                            result.warnings.add("Linha " + row.rowNumber + ": placa nao informada.");
                            continue;
                        }

                        String occurredAtIso = parseImportedOccurredAt(row.get(SafetySpreadsheetImporter.COLUMN_DATE));
                        if (occurredAtIso.isEmpty()) {
                            result.skippedRows++;
                            result.warnings.add("Linha " + row.rowNumber + ": data invalida na coluna Data.");
                            continue;
                        }

                        String driverName = safeImportedText(row.get(SafetySpreadsheetImporter.COLUMN_DRIVER), "Nao informado");
                        String location = safeImportedText(row.get(SafetySpreadsheetImporter.COLUMN_LOCATION), "Nao informado");
                        String description = resolveImportedDescription(row);

                        if (description.isEmpty()) {
                            result.skippedRows++;
                            result.warnings.add("Linha " + row.rowNumber + ": descricao da ocorrencia nao identificada.");
                            continue;
                        }

                        DriverEntity driver = ensureImportedDriver(driverName, now, result);
                        VehicleEntity vehicle = ensureImportedVehicle(plate, row.get(SafetySpreadsheetImporter.COLUMN_FLEET), now, result);
                        int occurrenceCount = parseOccurrenceCount(row.get(SafetySpreadsheetImporter.COLUMN_QUANTITY));
                        result.representedOccurrences += occurrenceCount;

                        SafetyEventEntity entity = new SafetyEventEntity(
                            resolveImportRecordId(row, result.fileName),
                            null,
                            mapImportedEventType(row),
                            occurredAtIso,
                            vehicle.plate,
                            vehicle.fleetCode,
                            vehicle.model,
                            driver.id,
                            driver.name,
                            location,
                            description,
                            mapImportedSeverity(row.get(SafetySpreadsheetImporter.COLUMN_SEVERITY)),
                            safeImportedText(row.get(SafetySpreadsheetImporter.COLUMN_REASON), ""),
                            buildImportedNotes(row, result.fileName, occurrenceCount),
                            hasImportedEvidence(row.get(SafetySpreadsheetImporter.COLUMN_HAS_EVIDENCE)) ? IMPORTED_EVIDENCE_MARKER : "",
                            RefuelEvidenceManager.CATEGORY_SAFETY_OCCURRENCE,
                            mapImportedAnalysisStatus(row.get(SafetySpreadsheetImporter.COLUMN_STATUS)),
                            occurrenceCount,
                            false,
                            "",
                            now,
                            now
                        );
                        entities.add(entity);
                        result.rowsImported++;
                    }

                    if (entities.isEmpty()) {
                        postError(callback, "Nenhuma ocorrencia valida foi encontrada na planilha selecionada.");
                        return;
                    }

                    safetyEventDao.insertAllReplace(entities);
                    preferences.updateLastSafetySpreadsheetImport(System.currentTimeMillis(), result.fileName);
                    postSuccess(callback, result);
                }
            } catch (Exception exception) {
                postError(callback, exception.getMessage() == null ? "Nao foi possivel importar a planilha de seguranca." : exception.getMessage());
            }
        });
    }

    public void updateSafetyEventStatus(long localId, String newStatus, RepositoryCallback<Void> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                SafetyEventEntity entity = safetyEventDao.findByLocalIdSync(localId);
                if (entity == null) {
                    postError(callback, "Evento nao encontrado.");
                    return;
                }
                entity.analysisStatus = newStatus == null || newStatus.trim().isEmpty() ? entity.analysisStatus : newStatus.trim();
                entity.updatedAt = System.currentTimeMillis();
                safetyEventDao.update(entity);
                postSuccess(callback, null);
            } catch (Exception exception) {
                postError(callback, exception.getMessage() == null ? "Nao foi possivel atualizar o status do evento." : exception.getMessage());
            }
        });
    }

    public void testConnection(RepositoryCallback<String> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            if (!AppPreferences.isValidBaseUrl(preferences.getApiBaseUrl())) {
                postError(callback, "URL invalida.");
                return;
            }
            try {
                ApiService service = ApiClientFactory.create(preferences.getApiBaseUrl());
                Response<HealthResponse> response = service.healthcheck().execute();
                if (response.isSuccessful() && response.body() != null && "ok".equalsIgnoreCase(response.body().status)) {
                    postSuccess(callback, "ok");
                } else {
                    postError(callback, "Resposta invalida do servidor.");
                }
            } catch (IOException exception) {
                postError(callback, exception.getMessage() == null ? "Falha na conexao." : exception.getMessage());
            }
        });
    }

    public void enqueueManualSync() {
        SyncScheduler.enqueueImmediate(appContext);
    }

    public void exportOperationalReport(Uri destinationUri, RepositoryCallback<String> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<RefuelEntity> refuels = refuelDao.getAllSync();
                try (OutputStream outputStream = appContext.getContentResolver().openOutputStream(destinationUri)) {
                    if (outputStream == null) {
                        postError(callback, "Nao foi possivel abrir o arquivo do relatorio.");
                        return;
                    }
                    try (OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8)) {
                        writer.write('\uFEFF');
                        writeCsvLine(
                            writer,
                            "tipo_abastecimento",
                            "placa",
                            "frota",
                            "modelo",
                            "motorista",
                            "litros",
                            "odometro_km",
                            "data_hora",
                            "local_posto",
                            "status",
                            "motivo_status",
                            "sincronizacao",
                            "observacao"
                        );

                        for (RefuelEntity refuel : refuels) {
                            writeCsvLine(
                                writer,
                                safeValue(refuel.fuelType),
                                safeValue(refuel.vehiclePlate),
                                safeValue(refuel.vehicleFleetCode),
                                safeValue(refuel.vehicleModel),
                                safeValue(refuel.driverName),
                                String.format(Locale.US, "%.2f", refuel.liters),
                                String.valueOf(refuel.odometerKm),
                                safeValue(refuel.suppliedAtIso),
                                safeValue(refuel.locationName),
                                safeValue(refuel.statusLevel),
                                safeValue(refuel.statusReason),
                                safeValue(refuel.syncStatus),
                                safeValue(refuel.notes)
                            );
                        }
                        writer.flush();
                    }
                }

                postSuccess(callback, "Relatorio exportado com sucesso.");
            } catch (Exception exception) {
                postError(
                    callback,
                    exception.getMessage() == null || exception.getMessage().trim().isEmpty()
                        ? "Nao foi possivel exportar o relatorio."
                        : exception.getMessage()
                );
            }
        });
    }

    public void exportOperationalReportPdf(Uri destinationUri, RepositoryCallback<String> callback) {
        ArlaDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<RefuelEntity> refuels = refuelDao.getAllSync();
                try (OutputStream outputStream = appContext.getContentResolver().openOutputStream(destinationUri)) {
                    if (outputStream == null) {
                        postError(callback, "Nao foi possivel abrir o arquivo PDF.");
                        return;
                    }

                    PdfDocument document = buildOperationalReportPdf(refuels);
                    try {
                        document.writeTo(outputStream);
                    } finally {
                        document.close();
                    }
                }

                postSuccess(callback, "PDF exportado com sucesso.");
            } catch (Exception exception) {
                postError(
                    callback,
                    exception.getMessage() == null || exception.getMessage().trim().isEmpty()
                        ? "Nao foi possivel exportar o PDF."
                        : exception.getMessage()
                );
            }
        });
    }

    public SyncExecutionResult synchronizeBlocking() {
        SyncExecutionResult result = new SyncExecutionResult();
        String baseUrl = preferences.getApiBaseUrl();
        if (!AppPreferences.isValidBaseUrl(baseUrl)) {
            result.message = "URL da API invalida.";
            return result;
        }
        if (!preferences.hasValidSession()) {
            result.message = "Login necessario para sincronizar.";
            return result;
        }

        ApiService service = ApiClientFactory.create(baseUrl, preferences);

        try {
            Response<List<VehicleResponse>> vehiclesResponse = service.listVehicles().execute();
            if (!vehiclesResponse.isSuccessful()) {
                return handleApiFailure(result, vehiclesResponse.code(), "Falha ao atualizar veiculos.");
            }
            if (vehiclesResponse.body() != null) {
                List<VehicleEntity> vehicles = new ArrayList<>();
                long now = System.currentTimeMillis();
                for (VehicleResponse response : vehiclesResponse.body()) {
                    vehicles.add(new VehicleEntity(
                        response.plate,
                        response.fleet_code == null ? "" : response.fleet_code,
                        response.model,
                        response.operation,
                        response.expected_fill_min_liters,
                        response.expected_fill_max_liters,
                        response.expected_per_1000_km_min,
                        response.expected_per_1000_km_max,
                        response.expected_diesel_fill_min_liters,
                        response.expected_diesel_fill_max_liters,
                        response.expected_diesel_km_per_liter_min,
                        response.expected_diesel_km_per_liter_max,
                        now
                    ));
                }
                vehicleDao.insertAll(vehicles);
            }
        } catch (IOException exception) {
            result.shouldRetry = true;
            result.message = "Falha ao atualizar veiculos.";
            return result;
        }

        List<SyncQueueEntity> queueItems = syncQueueDao.getAllSync();
        for (SyncQueueEntity queueItem : queueItems) {
            RefuelEntity refuel = refuelDao.findByLocalIdSync(queueItem.refuelLocalId);
            if (refuel == null) {
                syncQueueDao.deleteByRefuelLocalId(queueItem.refuelLocalId);
                continue;
            }

            syncQueueDao.registerAttempt(refuel.localId, System.currentTimeMillis());
            RecordCreateRequest request = toRemoteRequest(refuel);

            try {
                Response<RecordResponse> response = service.createRecord(request).execute();
                if (response.isSuccessful() && response.body() != null) {
                    applyRemoteSuccess(refuel, response.body());
                    refuelDao.update(refuel);
                    syncQueueDao.deleteByRefuelLocalId(refuel.localId);
                    result.syncedCount++;
                } else {
                    if (isUnauthorized(response.code())) {
                        return handleApiFailure(result, response.code(), "Sessao expirada. Faca login novamente.");
                    }
                    refuel.syncStatus = SyncState.FAILED;
                    refuel.syncError = "Servidor rejeitou o registro.";
                    refuelDao.update(refuel);
                    result.failedCount++;
                }
            } catch (IOException exception) {
                refuel.syncStatus = SyncState.FAILED;
                refuel.syncError = exception.getMessage() == null ? "Falha de rede." : exception.getMessage();
                refuelDao.update(refuel);
                result.failedCount++;
                result.shouldRetry = true;
            }
        }

        try {
            Response<List<RecordResponse>> recordsResponse = service.listRecords().execute();
            if (!recordsResponse.isSuccessful()) {
                return handleApiFailure(result, recordsResponse.code(), "Falha ao atualizar historico.");
            }
            syncRemoteRecords(recordsResponse.body());
        } catch (IOException exception) {
            result.shouldRetry = true;
            result.message = "Falha ao atualizar historico.";
            return result;
        }

        result.message = buildSyncMessage(result);
        preferences.updateLastSync(System.currentTimeMillis(), result.message);
        return result;
    }

    private DriverEntity resolveDriverForCurrentSession(long requestedDriverId) {
        AuthSession session = preferences.getSession();
        if (UserRole.isDriver(session.role)) {
            String linkedDriverName = session.linkedDriverName == null ? "" : session.linkedDriverName.trim();
            if (linkedDriverName.isEmpty()) {
                return null;
            }
            ensureDriverExists(linkedDriverName);
            return driverDao.findByNameSync(linkedDriverName);
        }
        return driverDao.findByIdSync(requestedDriverId);
    }

    private DriverEntity ensureImportedDriver(String driverName, long now, SafetyImportResult result) {
        DriverEntity existing = driverDao.findByNameSync(driverName);
        if (existing != null) {
            return existing;
        }
        long newId = driverDao.insert(new DriverEntity(driverName, true, now));
        result.createdDrivers++;
        DriverEntity created = driverDao.findByIdSync(newId);
        return created == null ? new DriverEntity(driverName, true, now) : created;
    }

    private VehicleEntity ensureImportedVehicle(String plate, String fleetCode, long now, SafetyImportResult result) {
        VehicleEntity existing = vehicleDao.findByPlateSync(plate);
        if (existing != null) {
            return existing;
        }

        VehicleEntity placeholder = new VehicleEntity(
            plate,
            safeImportedText(fleetCode, plate),
            "Importado via planilha",
            "Seguranca",
            0d,
            0d,
            0d,
            0d,
            0d,
            0d,
            0d,
            0d,
            now
        );
        vehicleDao.insertAll(Collections.singletonList(placeholder));
        result.createdVehicles++;
        VehicleEntity created = vehicleDao.findByPlateSync(plate);
        return created == null ? placeholder : created;
    }

    private String resolveImportRecordId(SafetySpreadsheetImporter.SpreadsheetRow row, String fileName) {
        String rowId = row.get(SafetySpreadsheetImporter.COLUMN_ID);
        if (!rowId.isEmpty()) {
            return rowId.trim();
        }
        return safeFileName(fileName) + "-row-" + row.rowNumber;
    }

    private String resolveImportedDescription(SafetySpreadsheetImporter.SpreadsheetRow row) {
        String description = safeImportedText(row.get(SafetySpreadsheetImporter.COLUMN_DESCRIPTION), "");
        if (!description.isEmpty()) {
            return description;
        }
        description = safeImportedText(row.get(SafetySpreadsheetImporter.COLUMN_NAME), "");
        if (!description.isEmpty()) {
            return description;
        }
        return safeImportedText(row.get(SafetySpreadsheetImporter.COLUMN_EVENT_DETAIL), "");
    }

    private String buildImportedNotes(SafetySpreadsheetImporter.SpreadsheetRow row, String fileName, int occurrenceCount) {
        List<String> notes = new ArrayList<>();
        notes.add("Importado via planilha: " + safeFileName(fileName));
        notes.add("Quantidade no arquivo: " + occurrenceCount);
        String status = safeImportedText(row.get(SafetySpreadsheetImporter.COLUMN_STATUS), "");
        if (!status.isEmpty()) {
            notes.add("Status original: " + status);
        }
        String observation = safeImportedText(row.get(SafetySpreadsheetImporter.COLUMN_LAST_NOTE), "");
        if (!observation.isEmpty()) {
            notes.add("Observacao: " + observation);
        }
        return String.join(" | ", notes);
    }

    private String mapImportedEventType(SafetySpreadsheetImporter.SpreadsheetRow row) {
        String combined = normalizeImportedText(
            resolveImportedDescription(row) + " " + row.get(SafetySpreadsheetImporter.COLUMN_EVENT_DETAIL)
        );
        if (combined.contains("acidente") || combined.contains("colisao") || combined.contains("batida") || combined.contains("tombamento")) {
            return SafetyEventType.ACCIDENT;
        }
        if (combined.contains("quase") || combined.contains("near miss")) {
            return SafetyEventType.NEAR_MISS;
        }
        if (combined.contains("condicao insegura") || combined.contains("condicao")) {
            return SafetyEventType.UNSAFE_CONDITION;
        }
        if (combined.contains("comportamento inseguro")
            || combined.contains("aceleracao")
            || combined.contains("velocidade")
            || combined.contains("freada")
            || combined.contains("fadiga")
            || combined.contains("curva")
            || combined.contains("celular")) {
            return SafetyEventType.UNSAFE_BEHAVIOR;
        }
        return SafetyEventType.INCIDENT;
    }

    private String mapImportedSeverity(String rawSeverity) {
        String normalized = normalizeImportedText(rawSeverity);
        if (normalized.contains("crit")) {
            return SafetySeverity.CRITICAL;
        }
        if (normalized.contains("grave") || normalized.contains("alta") || normalized.contains("alto")) {
            return SafetySeverity.HIGH;
        }
        if (normalized.contains("medio") || normalized.contains("moder")) {
            return SafetySeverity.MODERATE;
        }
        return SafetySeverity.LOW;
    }

    private String mapImportedAnalysisStatus(String rawStatus) {
        String normalized = normalizeImportedText(rawStatus);
        if (normalized.contains("finalizado") || normalized.contains("resolvido") || normalized.contains("tratado")) {
            return SafetyAnalysisStatus.RESOLVED;
        }
        if (normalized.contains("analise") || normalized.contains("revis")) {
            return SafetyAnalysisStatus.IN_REVIEW;
        }
        if (normalized.contains("aguard") || normalized.contains("pendente") || normalized.contains("anexo")) {
            return SafetyAnalysisStatus.ACTION_PENDING;
        }
        return SafetyAnalysisStatus.OPEN;
    }

    private String parseImportedOccurredAt(String rawValue) {
        String value = safeImportedText(rawValue, "");
        if (value.isEmpty()) {
            return "";
        }
        try {
            return LocalDateTime.parse(value, IMPORTED_DATE_TIME_SECONDS).withNano(0).toString();
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(value, IMPORTED_DATE_TIME).withNano(0).toString();
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(value, IMPORTED_DATE).atTime(LocalTime.of(8, 0)).toString();
        } catch (Exception ignored) {
        }

        Double serial = FormatUtils.parseFlexibleDecimal(value);
        if (serial != null && serial > 20000d) {
            int wholeDays = serial.intValue();
            int seconds = (int) Math.round((serial - wholeDays) * 24d * 60d * 60d);
            return LocalDateTime.of(1899, 12, 30, 0, 0).plusDays(wholeDays).plusSeconds(seconds).withNano(0).toString();
        }
        return "";
    }

    private int parseOccurrenceCount(String rawValue) {
        Double value = FormatUtils.parseFlexibleDecimal(rawValue);
        if (value == null || value <= 0d) {
            return 1;
        }
        return Math.max(1, value.intValue());
    }

    private boolean hasImportedEvidence(String rawValue) {
        String normalized = normalizeImportedText(rawValue);
        return normalized.contains("sim") || normalized.contains("evidenc");
    }

    private String sanitizePlate(String rawPlate) {
        String value = safeImportedText(rawPlate, "").replaceAll("[^A-Za-z0-9]", "");
        return value.toUpperCase(Locale.ROOT);
    }

    private String normalizeImportedText(String value) {
        String raw = safeImportedText(value, "");
        raw = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .trim();
        return raw;
    }

    private String safeImportedText(String value, String fallback) {
        String safe = value == null ? "" : value.trim();
        return safe.isEmpty() ? fallback : safe;
    }

    private String safeFileName(String fileName) {
        String safe = safeImportedText(fileName, "planilha_seguranca");
        return safe.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private void ensureDriverExists(String driverName) {
        if (driverName == null || driverName.trim().isEmpty()) {
            return;
        }
        DriverEntity existing = driverDao.findByNameSync(driverName.trim());
        if (existing != null) {
            return;
        }
        driverDao.insert(new DriverEntity(driverName.trim(), true, System.currentTimeMillis()));
    }

    private void syncRemoteRecords(List<RecordResponse> remoteRecords) {
        List<RefuelEntity> syncedRecords = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (remoteRecords != null) {
            for (RecordResponse response : remoteRecords) {
                ensureDriverExists(response.driver_name);
                DriverEntity driver = driverDao.findByNameSync(response.driver_name);
                VehicleEntity vehicle = vehicleDao.findByPlateSync(response.plate);
                RefuelEntity existingLocal = refuelDao.findByClientRecordIdSync(response.client_record_id);
                RefuelEntity syncedEntity = new RefuelEntity(
                    response.fuel_type == null ? "ARLA" : response.fuel_type,
                    response.client_record_id,
                    response.id,
                    response.plate,
                    response.fleet_code == null ? "" : response.fleet_code,
                    vehicle == null ? "Veiculo da frota" : vehicle.model,
                    driver == null ? 0L : driver.id,
                    response.driver_name,
                    response.liters,
                    response.odometer_km,
                    response.supplied_at,
                    response.location_name == null ? "" : response.location_name,
                    existingLocal == null ? null : existingLocal.totalAmount,
                    response.note == null ? "" : response.note,
                    existingLocal == null ? "" : existingLocal.evidencePhotoPath,
                    existingLocal == null ? "" : existingLocal.evidenceCategory,
                    existingLocal == null ? "" : existingLocal.checklistPayload,
                    existingLocal == null ? "" : existingLocal.checklistCompletedAtIso,
                    existingLocal == null ? RefuelEntryMode.MANUAL : existingLocal.dataEntryMode,
                    existingLocal == null ? "" : existingLocal.ocrStatus,
                    existingLocal == null ? "" : existingLocal.ocrRawText,
                    existingLocal == null ? "" : existingLocal.ocrMetadataJson,
                    response.status == null ? RefuelStatus.NORMAL : response.status,
                    response.analysis_reason == null ? "" : response.analysis_reason,
                    response.km_since_last_supply,
                    response.liters_per_1000_km,
                    response.km_per_liter,
                    SyncState.SYNCED,
                    "",
                    now,
                    now
                );
                syncedEntity.odometerInitialKm = existingLocal == null ? 0 : existingLocal.odometerInitialKm;
                syncedEntity.odometerFinalKm = existingLocal == null ? response.odometer_km : (existingLocal.odometerFinalKm > 0 ? existingLocal.odometerFinalKm : response.odometer_km);
                syncedEntity.calculatedArlaControlQuantity = existingLocal == null ? 0d : existingLocal.calculatedArlaControlQuantity;
                syncedEntity.expectedInitialOdometerKm = existingLocal == null ? null : existingLocal.expectedInitialOdometerKm;
                syncedEntity.odometerDivergenceKm = existingLocal == null ? null : existingLocal.odometerDivergenceKm;
                syncedEntity.pricePerLiter = existingLocal == null ? 0d : existingLocal.pricePerLiter;
                syncedEntity.costPerKm = existingLocal == null ? 0d : existingLocal.costPerKm;
                syncedRecords.add(syncedEntity);
            }
        }
        refuelDao.deleteSyncedRecords();
        if (!syncedRecords.isEmpty()) {
            refuelDao.insertAllReplace(syncedRecords);
        }
    }

    private void clearLocalSessionData() {
        for (RefuelEntity entity : refuelDao.getAllSync()) {
            RefuelEvidenceManager.deleteQuietly(entity.evidencePhotoPath);
        }
        for (SafetyEventEntity entity : safetyEventDao.getAllSync()) {
            RefuelEvidenceManager.deleteQuietly(entity.evidencePhotoPath);
        }
        syncQueueDao.clearAll();
        refuelDao.clearAll();
        safetyEventDao.clearAll();
        preferences.updateLastSync(0L, "");
    }

    private String appendReason(String baseReason, String newReason) {
        String base = baseReason == null ? "" : baseReason.trim();
        String addition = newReason == null ? "" : newReason.trim();
        if (addition.isEmpty()) {
            return base;
        }
        if (base.isEmpty()) {
            return addition;
        }
        if (base.contains(addition)) {
            return base;
        }
        return base + " | " + addition;
    }

    private SyncExecutionResult handleApiFailure(SyncExecutionResult result, int code, String fallbackMessage) {
        if (isUnauthorized(code)) {
            preferences.clearSession();
            result.message = "Sessao expirada. Faca login novamente.";
            return result;
        }
        result.message = fallbackMessage;
        return result;
    }

    private boolean isUnauthorized(int code) {
        return code == 401 || code == 403;
    }

    private String buildSyncMessage(SyncExecutionResult result) {
        if (result.syncedCount == 0 && result.failedCount == 0) {
            return "Dados atualizados com sucesso.";
        }
        return result.syncedCount + " sincronizado(s), " + result.failedCount + " com falha.";
    }

    private RecordCreateRequest toRemoteRequest(RefuelEntity refuel) {
        RecordCreateRequest request = new RecordCreateRequest();
        request.client_record_id = refuel.clientRecordId;
        request.fuel_type = refuel.fuelType;
        request.plate = refuel.vehiclePlate;
        request.fleet_code = refuel.vehicleFleetCode;
        request.driver_name = refuel.driverName;
        request.liters = refuel.liters;
        request.odometer_km = refuel.odometerKm;
        request.location_name = refuel.locationName;
        request.note = refuel.notes;
        request.supplied_at = refuel.suppliedAtIso;
        return request;
    }

    private void applyRemoteSuccess(RefuelEntity refuel, RecordResponse response) {
        refuel.remoteId = response.id;
        refuel.fuelType = response.fuel_type == null ? refuel.fuelType : response.fuel_type;
        refuel.vehicleFleetCode = response.fleet_code == null ? refuel.vehicleFleetCode : response.fleet_code;
        refuel.statusLevel = response.status == null ? RefuelStatus.NORMAL : response.status;
        refuel.statusReason = response.analysis_reason == null ? refuel.statusReason : response.analysis_reason;
        refuel.kmSinceLastSupply = response.km_since_last_supply;
        refuel.litersPer1000Km = response.liters_per_1000_km;
        refuel.kmPerLiter = response.km_per_liter;
        refuel.syncStatus = SyncState.SYNCED;
        refuel.syncError = "";
        refuel.syncedAt = System.currentTimeMillis();
    }

    private AuthSession mapSession(AuthResponse response) {
        AuthSession session = new AuthSession();
        session.accessToken = response.token == null ? "" : response.token;
        session.expiresAtIso = response.expires_at == null ? "" : response.expires_at;
        session.userId = response.user == null ? 0L : response.user.id;
        session.fullName = response.user == null || response.user.full_name == null ? "" : response.user.full_name;
        session.email = response.user == null || response.user.email == null ? "" : response.user.email;
        session.role = response.user == null || response.user.role == null ? "" : response.user.role;
        session.linkedDriverName = response.user == null || response.user.linked_driver_name == null ? "" : response.user.linked_driver_name;
        return session;
    }

    private <T> void postSuccess(RepositoryCallback<T> callback, T value) {
        mainHandler.post(() -> callback.onSuccess(value));
    }

    private <T> void postError(RepositoryCallback<T> callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private void writeCsvLine(OutputStreamWriter writer, String... values) throws IOException {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) {
                builder.append(';');
            }
            builder.append('"')
                .append(csvEscape(values[index]))
                .append('"');
        }
        builder.append('\n');
        writer.write(builder.toString());
    }

    private String csvEscape(String value) {
        return safeValue(value).replace("\"", "\"\"");
    }

    private String safeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private PdfDocument buildOperationalReportPdf(List<RefuelEntity> refuels) {
        PdfDocument document = new PdfDocument();
        int pageWidth = 595;
        int pageHeight = 842;
        int margin = 32;
        int lineHeight = 16;

        Paint titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        titlePaint.setTextSize(16f);

        Paint metaPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        metaPaint.setTextSize(10f);

        Paint sectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        sectionPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        sectionPaint.setTextSize(11f);

        Paint bodyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setTextSize(10f);

        int pageNumber = 1;
        PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
        Canvas canvas = page.getCanvas();
        float y = margin;

        y = drawPageHeader(canvas, titlePaint, metaPaint, margin, y, pageNumber, refuels.size());

        for (int index = 0; index < refuels.size(); index++) {
            RefuelEntity refuel = refuels.get(index);
            int blockHeight = 100;
            if (y + blockHeight > pageHeight - margin) {
                document.finishPage(page);
                pageNumber++;
                page = document.startPage(new PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create());
                canvas = page.getCanvas();
                y = margin;
                y = drawPageHeader(canvas, titlePaint, metaPaint, margin, y, pageNumber, refuels.size());
            }

            y = drawRefuelBlock(canvas, refuel, margin, y, pageWidth - margin, lineHeight, sectionPaint, bodyPaint);
            if (index < refuels.size() - 1) {
                y += 6;
            }
        }

        if (refuels.isEmpty()) {
            canvas.drawText("Nenhum abastecimento disponivel para exportacao.", margin, y + 20, bodyPaint);
        }

        document.finishPage(page);
        return document;
    }

    private float drawPageHeader(
        Canvas canvas,
        Paint titlePaint,
        Paint metaPaint,
        int margin,
        float startY,
        int pageNumber,
        int totalRecords
    ) {
        canvas.drawText("Relatorio de abastecimentos", margin, startY, titlePaint);
        canvas.drawText("Gerado em: " + FormatUtils.formatDateTime(java.time.LocalDateTime.now().toString()), margin, startY + 16, metaPaint);
        canvas.drawText("Registros: " + totalRecords + "  |  Pagina: " + pageNumber, margin, startY + 30, metaPaint);
        return startY + 52;
    }

    private float drawRefuelBlock(
        Canvas canvas,
        RefuelEntity refuel,
        int startX,
        float startY,
        int rightLimit,
        int lineHeight,
        Paint sectionPaint,
        Paint bodyPaint
    ) {
        canvas.drawText(
            FormatUtils.formatFuelType(appContext, refuel.fuelType) + " | " + safeValue(refuel.vehiclePlate) + " | " + safeValue(refuel.driverName),
            startX,
            startY,
            sectionPaint
        );

        float y = startY + lineHeight;
        canvas.drawText("Frota: " + safeValue(refuel.vehicleFleetCode) + " | Modelo: " + safeValue(refuel.vehicleModel), startX, y, bodyPaint);
        y += lineHeight;
        canvas.drawText("Litros: " + FormatUtils.formatLiters(refuel.liters) + " | Odometro: " + FormatUtils.formatKilometers(refuel.odometerKm), startX, y, bodyPaint);
        y += lineHeight;
        canvas.drawText("Data/hora: " + FormatUtils.formatDateTime(refuel.suppliedAtIso), startX, y, bodyPaint);
        y += lineHeight;
        canvas.drawText("Local: " + safeValue(refuel.locationName), startX, y, bodyPaint);
        y += lineHeight;
        canvas.drawText(
            "Status: " + FormatUtils.formatStatus(appContext, refuel.statusLevel) + " | Sync: " + FormatUtils.formatSyncStatus(appContext, refuel.syncStatus),
            startX,
            y,
            bodyPaint
        );
        y += lineHeight;

        String notes = safeValue(refuel.notes);
        if (notes.isEmpty()) {
            notes = "Sem observacoes";
        }
        for (String line : wrapText("Obs: " + notes, bodyPaint, rightLimit - startX)) {
            canvas.drawText(line, startX, y, bodyPaint);
            y += lineHeight;
        }

        canvas.drawLine(startX, y - 4, rightLimit, y - 4, bodyPaint);
        return y + 6;
    }

    private List<String> wrapText(String text, Paint paint, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String safeText = safeValue(text);
        if (safeText.isEmpty()) {
            lines.add("");
            return lines;
        }

        String[] words = safeText.split("\\s+");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String candidate = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (paint.measureText(candidate) <= maxWidth) {
                currentLine.setLength(0);
                currentLine.append(candidate);
            } else {
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine.setLength(0);
                }
                currentLine.append(word);
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }
}
