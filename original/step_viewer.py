# Compilation mode, support OS-specific options
# nuitka-project: --standalone

# The PyQt5 plugin covers qt-plugins
# nuitka-project: --enable-plugin=pyqt5

# Include material density
# nuitka-project: --include-data-files={MAIN_DIRECTORY}/material_density.json=material_density.json

# Include icon
# nuitka-project: --include-data-files={MAIN_DIRECTORY}/favicon.ico=favicon.ico

# exe icon
# nuitka-project: --windows-icon-from-ico={MAIN_DIRECTORY}/favicon.ico

# Turn off console
# nuitka-project: --disable-console

# Include package data so the cursor icons are available
# nuitka-project: --include-package-data=OCC


import sys
import os
import json
import math
from PyQt5.QtWidgets import *
from PyQt5 import QtGui
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QFont
from OCC.Core.STEPControl import STEPControl_Reader
from OCC.Core.Bnd import Bnd_Box
from OCC.Core.BRepBndLib import brepbndlib
from OCC.Core.GProp import GProp_GProps
from OCC.Core.BRepGProp import brepgprop
from OCC.Core.gp import *
from OCC.Core.BRepPrimAPI import *
from OCC.Core.BRep import BRep_Tool
from OCC.Core.BRepBuilderAPI import BRepBuilderAPI_MakeEdge
from OCC.Core.AIS import AIS_Shape
from OCC.Core.Quantity import Quantity_Color, Quantity_TOC_RGB
from OCC.Core.Prs3d import Prs3d_LineAspect
from OCC.Core.Aspect import Aspect_TOL_SOLID

from OCC.Display.backend import load_backend
used_backend = load_backend('pyqt5')
from OCC.Display.qtDisplay import qtViewer3d

DEBUG = False
# check if there is a file named debug.key
if os.path.exists("debug.key"):
    # if in the file there is a text string "06ad6620-ba59-4962-809f-c4dc82e61412"
    with open("debug.key", "r", encoding="utf-8") as f:
        key = f.read()
        if key == "06ad6620-ba59-4962-809f-c4dc82e61412":
            DEBUG = True

# get current directory and make the path to the material density data file
script_dir = os.path.dirname(os.path.realpath(__file__))
density_data_path = os.path.join(script_dir, "material_density.json")
MATERIAL_DENSITY: dict = json.load(open(density_data_path, encoding="utf-8"))


# fix different screen resolution
if hasattr(Qt, 'AA_EnableHighDpiScaling'):
    QApplication.setAttribute(Qt.AA_EnableHighDpiScaling, True)

if hasattr(Qt, 'AA_UseHighDpiPixmaps'):
    QApplication.setAttribute(Qt.AA_UseHighDpiPixmaps, True)


def get_shape(step_file):
    """
    Initialize a STEP reader, read a STEP file, transfer roots, get a shape from the reader, and return the shape.
    
    Parameters:
    step_file (str): The path to the STEP file to read.
    
    Returns:
    TopoDS_Shape: The shape read from the STEP file.
    """
    # Initialize STEP reader
    step_reader = STEPControl_Reader()
    step_reader.ReadFile(step_file)
    step_reader.TransferRoots()

    # Get the shape from the reader
    shape = step_reader.Shape()  # Assuming there's only one shape in the file

    return shape

def calculate_bounding_box(shape):
    """
    Calculate the bounding box of a given shape.

    Parameters:
    shape : TopoDS_Shape
        The shape for which the bounding box needs to be calculated.

    Returns:
    Bnd_Box
        The bounding box of the input shape.
    """
    bbox = Bnd_Box()
    brepbndlib.AddClose(shape, bbox)
    return bbox

def calculate_volume(shape, tolerance=1.0e-5):
    """
    Calculate the volume of a given shape.

    Parameters:
    shape : TopoDS_Shape
        The shape for which the volume needs to be calculated.

    Returns:
    float
        The volume of the input shape.
    """
    volume = GProp_GProps()
    brepgprop.VolumeProperties(shape, volume, tolerance)
    return volume.Mass()

def calculate_surface_area(shape, tolerance=1.0e-5):
    """
    Calculate the surface area of a given shape.

    Parameters:
    shape : TopoDS_Shape
        The shape for which the surface area needs to be calculated.

    Returns:
    float
        The surface area of the input shape.
    """
    surface_area = GProp_GProps()
    brepgprop.SurfaceProperties(shape, surface_area, tolerance)
    return surface_area.Mass()

def calculate_distance_between_points(x1, y1, z1, x2, y2, z2):
    return math.sqrt((x2 - x1) ** 2 + (y2 - y1) ** 2 + (z2 - z1) ** 2)

def create_colored_line(p1: gp_Pnt, p2: gp_Pnt, color, width=1):
    edge = BRepBuilderAPI_MakeEdge(p1, p2).Edge()
    ais_shape = AIS_Shape(edge)
    ais_shape.SetColor(Quantity_Color(*color, Quantity_TOC_RGB))
    
    # Set line aspect (width and style)
    drawer = ais_shape.Attributes()
    aspect = Prs3d_LineAspect(Quantity_Color(*color, Quantity_TOC_RGB), Aspect_TOL_SOLID, width)
    drawer.SetWireAspect(aspect)
    ais_shape.SetAttributes(drawer)

    return ais_shape


class MaxLengthList(list):
    def __init__(self, max_length):
        super().__init__()
        self.max_length = max_length

    def append(self, item):
        super().append(item)
        if len(self) > self.max_length:
            self.pop(0)

class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.font_size = 14
        self.__is_measuring = False
        self.__vertexs = MaxLengthList(2)
        self.__measure_line = None
        # set font
        self.setFont(QFont("黑体", self.font_size))
        # set icon
        self.setWindowIcon(QtGui.QIcon(os.path.join(script_dir, "favicon.ico")))
        self.InitUI()
        self.canva.InitDriver()
        self.setAcceptDrops(True)
        
        if DEBUG:
            self.__drawAYellowBall()
            self.display.FitAll()
    
    def InitUI(self):
        self.setWindowTitle("STEP File Viewer")
        self.setMinimumHeight(600)

        # Main widget and layout
        self.central_widget = QWidget()
        self.setCentralWidget(self.central_widget)
        self.main_layout = QHBoxLayout()
        self.central_widget.setLayout(self.main_layout)
        
        # Left widget and Right widget setup
        self.left_widget = QWidget()
        self.right_widget = QWidget()
        self.left_layout = QGridLayout()
        self.left_widget.setMinimumWidth(650)
        self.right_layout = QGridLayout()
        # set the width of the right widget always 1/3 of the main window
        self.right_widget.setFixedHeight(480)
        # self.right_widget.setMinimumWidth(400)
        self.left_widget.setLayout(self.left_layout)
        self.right_widget.setLayout(self.right_layout)

        # Add left and right widgets to main layout
        self.main_layout.addWidget(self.left_widget, 2)
        self.main_layout.addWidget(self.right_widget, 1)
        
        # 3d viewer
        self.canva = qtViewer3d(self.left_widget)
        self.display = self.canva._display
        self.display.display_triedron()
        self.display.set_bg_gradient_color([18, 20, 23], [92, 101, 113])
        self.display.register_select_callback(self.select_callback)
        self.left_layout.addWidget(self.canva, 0, 0)
                
        # Button to load STEP file
        self.load_button = QPushButton("加载STEP文件")
        self.load_button.clicked.connect(self.openFileDialog)
        self.right_layout.addWidget(self.load_button, 1, 0)

        # Widget and grid layout for information display
        self.info_widget = QWidget()
        self.info_layout = QGridLayout()
        self.info_widget.setLayout(self.info_layout)
        self.right_layout.addWidget(self.info_widget, 0, 0)

        # Labels and LineEdit setup
        labels = [
            "filename", "height", "width", 
            "length", "volume", "mass"
            ]
        labels_text = [
            "名称:", "高度:", "宽度:", 
            "长度:", "体积:", "质量:"
        ]
        units = [
            "", "mm", "mm",
            "mm", "mm^3", "g"
        ]
        self.line_edits = {}
        self.copy_buttons = {}
        self.unit_labels = {}
        
        for i, text in enumerate(labels):
            label = QLabel(labels_text[i])
            line_edit = QLineEdit()
            line_edit.setReadOnly(True)
            unit_label = QLabel(units[i])
            copy_button = QPushButton("复制")
            copy_button.clicked.connect(self.__copyTextInLineEdits(line_edit))
            
            self.line_edits[text] = line_edit
            self.unit_labels[text] = unit_label
            self.copy_buttons[text] = copy_button
            
            self.info_layout.addWidget(label, i, 0)
            self.info_layout.addWidget(line_edit, i, 1)
            self.info_layout.addWidget(unit_label, i, 2)
            self.info_layout.addWidget(copy_button, i, 3)
            
        # Material ComboBox setup
        self.material_label = QLabel("材料:")
        self.material_combo = QComboBox()
        self.material_combo.view().setTextElideMode(Qt.ElideRight)
        self.material_combo.addItems(list(MATERIAL_DENSITY.keys()))
        self.material_combo.currentIndexChanged.connect(self.updateMass)
        self.info_layout.addWidget(self.material_label, len(labels), 0)
        self.info_layout.addWidget(self.material_combo, len(labels), 1, 1, 3)
        
        # check box widget
        self.check_box_widget = QWidget()
        self.check_box_layout = QGridLayout()
        self.check_box_layout.setContentsMargins(0, 20, 0, 0)
        self.check_box_widget.setLayout(self.check_box_layout)
        self.right_layout.addWidget(self.check_box_widget, 2, 0)

        # check box for always on top
        self.ontop_check_box = QCheckBox("窗口置顶")
        self.ontop_check_box.setChecked(False)
        self.ontop_check_box.stateChanged.connect(self.__toggle_always_on_top)
        self.check_box_layout.addWidget(self.ontop_check_box, 0, 0)
        
        # check box for measuring
        self.measure_check_box = QCheckBox("启用测量")
        self.measure_check_box.setChecked(self.__is_measuring)
        self.measure_check_box.stateChanged.connect(self.__toggle_measuring)
        # disable the checkbox by default
        self.measure_check_box.setEnabled(False)
        self.check_box_layout.addWidget(self.measure_check_box, 0, 1)
        
        self.distance_widget = QWidget()
        self.distance_widget.setEnabled(self.__is_measuring)
        # set the widget no padding and no margin
        self.distance_layout = QGridLayout()
        self.distance_layout.setContentsMargins(0, 0, 0, 0)
        self.distance_widget.setLayout(self.distance_layout)
        self.check_box_layout.addWidget(self.distance_widget, 1, 0, 1, 2)
        self.distance_line_edit = QLineEdit()
        self.distance_unit_label = QLabel("mm")
        self.distance_copy_button = QPushButton("复制")
        self.distance_copy_button.clicked.connect(self.__copyTextInLineEdits(self.distance_line_edit))
        self.distance_layout.addWidget(self.distance_line_edit, 0, 0)
        self.distance_layout.addWidget(self.distance_unit_label, 0, 1)
        self.distance_layout.addWidget(self.distance_copy_button, 0, 2)
        
    def openFileDialog(self):
        options = QFileDialog.Options()
        fileName, _ = QFileDialog.getOpenFileName(self, "加载STEP文件", "",
                                                  "STEP Files (*.stp *.step);;All Files (*)", options=options)
        if fileName:            
            self.loadFile(fileName)
        else:
            if DEBUG:
                # Here you would process the file and update the GUI accordingly
                self.line_edits['filename'].setText(fileName)
                # Example static update
                self.line_edits['height'].setText("100 mm")
                self.line_edits['width'].setText("200 mm")
                self.line_edits['length'].setText("300 mm")
                self.line_edits['mass'].setText("400 g")
    
    def __toggle_measuring(self):
        self.__is_measuring = self.measure_check_box.isChecked()
        self.distance_widget.setEnabled(self.__is_measuring)
        if self.__is_measuring:
            # it means the user wants to start measuring
            mode = 7 # TopAbs_ShapeEnum.TopAbs_VERTEX
            self.canva._key_map.pop(ord("G"), None)
        else:
            # it means the user wants to stop measuring
            mode = 2 # TopAbs_ShapeEnum.TopAbs_SOLID
            self.canva._key_map[ord("G")] = self.display.SetSelectionMode
            # init related variables
            self.__vertexs.clear()
            self.display.Context.Erase(self.__measure_line, True)
            self.__measure_line = None
            self.distance_line_edit.setText("")
        self.display.SetSelectionMode(mode)
    
    def select_callback(self, shapes, *pos):
        # print("select_callback", shapes)
        # print(pos)
        if not self.__is_measuring: return
        if len(shapes) == 0: return
        
        shapes = shapes[:2]
        for shape in shapes:
            self.__vertexs.append(shape)
        
        if len(self.__vertexs) == 2:
            vt1 = self.__vertexs[0]
            vt2 = self.__vertexs[1]
            vt1_pnt = BRep_Tool.Pnt(vt1)
            vt2_pnt = BRep_Tool.Pnt(vt2)
            x1, y1, z1 = vt1_pnt.X(), vt1_pnt.Y(), vt1_pnt.Z()
            x2, y2, z2 = vt2_pnt.X(), vt2_pnt.Y(), vt2_pnt.Z()
            distance = math.sqrt((x1 - x2)**2 + (y1 - y2)**2 + (z1 - z2)**2)
            self.distance_line_edit.setText(f"{distance:.2f}")
            
            self.display.Context.Erase(self.__measure_line, True)
            self.__measure_line = create_colored_line(vt1_pnt, vt2_pnt, (0,0,1), 2)
            self.display.Context.Display(self.__measure_line, True)

    def __copyTextInLineEdits(self, line_edit):
        def copy_text():
            text = line_edit.text()
            if not text: return
            QApplication.clipboard().setText(text)
        return copy_text
    
    def loadFile(self, fileName):
        self.display.EraseAll()
        # Load STEP file
        shape = get_shape(fileName)
        self.display.DisplayColoredShape(shape, "BLACK")
        # Display information
        short_filename = os.path.basename(fileName)
        short_filename = os.path.splitext(short_filename)[0]
        self.line_edits['filename'].setText(short_filename)
        self.updateInfo(shape)
        self.display.FitAll()
        self.measure_check_box.setEnabled(True)

    def updateInfo(self, shape):
        bbox = calculate_bounding_box(shape)
        xmin, ymin, zmin, xmax, ymax, zmax = bbox.Get()
        self.line_edits['height'].setText(f"{(zmax - zmin):.2f}")
        self.line_edits['width'].setText(f"{(xmax - xmin):.2f}")
        self.line_edits['length'].setText(f"{(ymax - ymin):.2f}")
        
        volume = calculate_volume(shape)
        self.line_edits['volume'].setText(f"{volume:.2f}")
        mass = volume * MATERIAL_DENSITY[self.material_combo.currentText()]
        self.line_edits['mass'].setText(f"{mass:.2f}")
    
    def updateMass(self):
        ma = self.material_combo.currentText()
        de = MATERIAL_DENSITY[ma]
        try:
            vo = float(self.line_edits['volume'].text())
        except ValueError:
            return
        ma = vo * de
        self.line_edits['mass'].setText(f"{ma:.2f}")
                
    def centerOnScreen(self):
        qr = self.frameGeometry()
        cp = QtGui.QGuiApplication.primaryScreen().availableGeometry().center()
        qr.moveCenter(cp)
        self.move(qr.topLeft())
    
    def __toggle_always_on_top(self, state):
        if state ==  Qt.Checked:
            self.setWindowFlags(self.windowFlags() | Qt.WindowStaysOnTopHint)
        else:
            self.setWindowFlags(self.windowFlags() & ~Qt.WindowStaysOnTopHint)
        self.show()  # Need to call show() to apply the new flags
    
    # allow drag and drop
    def dragEnterEvent(self, event):
        if event.mimeData().hasUrls():
            event.acceptProposedAction()
        else:
            event.ignore()
    
    def dragMoveEvent(self, event):
        if event.mimeData().hasUrls():
            event.acceptProposedAction()
        else:
            event.ignore()
    
    def dropEvent(self, event):
        if event.mimeData().hasUrls():
            event.acceptProposedAction()
            # filter the file extension
            url = [url.toLocalFile() for url in event.mimeData().urls() if url.toLocalFile().lower().endswith('.stp') or url.toLocalFile().lower().endswith('.step')]
            if not url:
                event.ignore()
                return
            url = url[0]
            self.loadFile(url)
        else:
            event.ignore()
        
    def __drawAYellowBall(self):
        Radius = 50.0
        # The sphere center
        X1 = 25.0
        Y1 = 50.0
        Z1 = 50.0
        # create OCC.gp.gp_Pnt-Point from vector
        Point = gp_Pnt( X1, Y1, Z1 )
        MySphere = BRepPrimAPI_MakeSphere( Point, Radius )
        MySphereShape = MySphere.Shape()
        self.display.DisplayColoredShape( MySphereShape , 'YELLOW' )
    
    


if __name__ == '__main__':        
    app = QApplication(sys.argv)
    mainWin = MainWindow()
    mainWin.resize(mainWin.width()-1, mainWin.height()-1)
    mainWin.show()
    # make the 3d viewer have right size
    mainWin.resize(mainWin.width()+1, mainWin.height()+1)
    mainWin.centerOnScreen()
    
    if len(sys.argv) > 1:
        fileName = sys.argv[1]
        mainWin.loadFile(fileName)

    sys.exit(app.exec_())
